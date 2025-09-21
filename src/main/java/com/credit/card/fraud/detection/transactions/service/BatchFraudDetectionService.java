package com.credit.card.fraud.detection.transactions.service;

import com.credit.card.fraud.detection.transactions.entity.Transaction;
import com.credit.card.fraud.detection.transactions.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchFraudDetectionService {

    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;

    private static final int PROCESSING_BATCH_SIZE = 10000; // 성능 최적화를 위해 배치 크기 증가
    private static final int PARALLEL_THREADS = 16;  // 병렬 처리 스레드 수 추가 증가
    private static final int EXECUTOR_SHUTDOWN_TIMEOUT = 600;  // 10분 대기
    private static final int PROGRESS_LOG_INTERVAL = 10000;  // 1만건마다 진행 로그
    private static final int MEMORY_CLEANUP_INTERVAL = 5;  // 5 청크마다 메모리 정리

    /**
     * PENDING 상태의 거래들에 대해 병렬로 사기 탐지 수행 (최적화된 버전)
     */
    @Async
    @CacheEvict(value = "pendingCount", key = "'pending_transaction_count'")
    public CompletableFuture<Void> processPendingTransactions() {
        log.info("시작: PENDING 상태 거래 사기 탐지 처리 (병렬 처리)");

        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(PARALLEL_THREADS);

        try {
            Pageable pageable = PageRequest.of(0, PROCESSING_BATCH_SIZE);
            Page<Transaction> pendingTransactions;

            do {
                List<Long> transactionIds = transactionRepository.findPendingTransactionIds(pageable);

                if (!transactionIds.isEmpty()) {
                    List<Transaction> transactions = transactionRepository.findTransactionsWithAssociationsByIds(transactionIds);
                    pendingTransactions = new PageImpl<>(transactions, pageable, transactions.size());

                    // 병렬 처리를 위해 CompletableFuture 리스트 생성
                    List<CompletableFuture<Void>> futures = transactions.stream()
                        .map(transaction -> CompletableFuture.runAsync(() -> {
                            try {
                                transactionService.processAndDetectFraud(transaction);
                                int processed = processedCount.incrementAndGet();

                                if (processed % 100 == 0) {
                                    log.info("사기 탐지 처리 진행: {} 건 완료", processed);
                                }

                            } catch (Exception e) {
                                failedCount.incrementAndGet();
                                log.error("거래 {} 사기 탐지 실패: {}",
                                    transaction.getId(), e.getMessage());
                                markTransactionAsError(transaction);
                            }
                        }, executor))
                        .toList();

                    // 현재 배치의 모든 작업 완료 대기
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                } else {
                    pendingTransactions = Page.empty();
                }

                pageable = pageable.next();

            } while (transactionIds.size() >= PROCESSING_BATCH_SIZE);

            log.info("완료: PENDING 상태 거래 사기 탐지 처리 - 처리: {}, 실패: {}",
                processedCount.get(), failedCount.get());

        } catch (Exception e) {
            log.error("배치 사기 탐지 처리 중 오류 발생", e);
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT, TimeUnit.SECONDS)) {
                    log.warn("Executor 종료 타임아웃, 강제 종료 실행");
                    executor.shutdownNow();
                    if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                        log.error("Executor 강제 종료 실패");
                    }
                }
            } catch (InterruptedException e) {
                log.warn("Executor 종료 중 인터럽트 발생");
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * 청크 단위로 대량 PENDING 거래 처리 (메모리 효율적)
     */
    @Async
    @CacheEvict(value = "pendingCount", key = "'pending_transaction_count'")
    public CompletableFuture<Void> processLargePendingTransactionsInChunks() {
        log.info("시작: 대량 PENDING 거래 청크 단위 처리");

        AtomicInteger totalProcessed = new AtomicInteger(0);
        AtomicInteger totalFailed = new AtomicInteger(0);

        try {
            long totalPending = getPendingTransactionCountForced();
            log.info("처리 대상 PENDING 거래: {} 건", totalPending);

            if (totalPending == 0) {
                log.info("처리할 PENDING 거래가 없습니다");
                return CompletableFuture.completedFuture(null);
            }

            int pageNumber = 0;
            boolean hasMore = true;

            while (hasMore) {
                List<Transaction> transactions = processTransactionChunkWithConnection(pageNumber);

                if (transactions.isEmpty()) {
                    hasMore = false;
                    continue;
                }

                // 현재 청크 처리 (배치 최적화)
                List<Transaction> processedTransactions = transactionService.processBatchTransactions(transactions);
                totalProcessed.addAndGet(processedTransactions.size());

                // 처리 상태 로깅 (더 자주)
                if (totalProcessed.get() % PROGRESS_LOG_INTERVAL == 0) {
                    long remaining = getPendingTransactionCountForced();
                    double progress = ((double) totalProcessed.get() / totalPending) * 100;
                    log.info("처리 진행: {:.1f}% ({} / {} 건), 남은 거래: {} 건",
                        progress, totalProcessed.get(), totalPending, remaining);
                }

                hasMore = transactions.size() >= PROCESSING_BATCH_SIZE;
                pageNumber++;

                // 메모리 정리를 위한 짧은 대기 (더 자주)
                if (pageNumber % MEMORY_CLEANUP_INTERVAL == 0) {
                    try {
                        Thread.sleep(200);  // 조금 더 긴 대기
                        System.gc();
                        log.debug("메모리 정리 수행 - 청크 {}", pageNumber);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            log.info("완료: 대량 PENDING 거래 처리 - 총 처리: {} 건, 실패: {} 건",
                totalProcessed.get(), totalFailed.get());

        } catch (Exception e) {
            log.error("대량 배치 처리 중 오류 발생", e);
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * 트랜잭션 범위에서 연관 데이터와 함께 거래 로드
     */
    @Transactional(readOnly = true)
    protected List<Transaction> processTransactionChunkWithConnection(int pageNumber) {
        Pageable pageable = PageRequest.of(pageNumber, PROCESSING_BATCH_SIZE);

        try {
            // 먼저 ID만 조회 (페이징 적용)
            List<Long> transactionIds = transactionRepository.findPendingTransactionIds(pageable);

            if (transactionIds.isEmpty()) {
                return List.of();
            }

            // ID 리스트로 연관 데이터와 함께 조회
            return transactionRepository.findTransactionsWithAssociationsByIds(transactionIds);
        } catch (Exception e) {
            log.error("거래 청크 로딩 실패 (페이지: {}): {}", pageNumber, e.getMessage());
            return List.of();
        }
    }

    /**
     * 단일 청크 처리 (병렬)
     */
    private int processTransactionChunk(List<Transaction> transactions) {
        AtomicInteger processedCount = new AtomicInteger(0);
        ExecutorService chunkExecutor = Executors.newFixedThreadPool(PARALLEL_THREADS);

        try {
            List<CompletableFuture<Void>> futures = transactions.stream()
                .map(transaction -> CompletableFuture.runAsync(() -> {
                    try {
                        transactionService.processAndDetectFraud(transaction);
                        processedCount.incrementAndGet();
                    } catch (Exception e) {
                        log.error("거래 {} 처리 실패: {}", transaction.getId(), e.getMessage());
                        markTransactionAsError(transaction);
                    }
                }, chunkExecutor))
                .toList();

            // 현재 청크의 모든 작업 완료 대기
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .orTimeout(300, TimeUnit.SECONDS) // 5분 타임아웃
                .join();

        } catch (Exception e) {
            log.error("청크 처리 중 오류: {}", e.getMessage());
        } finally {
            chunkExecutor.shutdown();
            try {
                if (!chunkExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                    chunkExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                chunkExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        return processedCount.get();
    }

    /**
     * 특정 개수만큼 PENDING 거래 처리
     */
    @Async
    @CacheEvict(value = "pendingCount", key = "'pending_transaction_count'")
    public CompletableFuture<Integer> processPendingTransactions(int limit) {
        log.info("시작: PENDING 상태 거래 {} 건 처리", limit);

        int processedCount = 0;

        try {
            Pageable pageable = PageRequest.of(0, Math.min(limit, PROCESSING_BATCH_SIZE));
            List<Long> transactionIds = transactionRepository.findPendingTransactionIds(pageable);

            while (!transactionIds.isEmpty() && processedCount < limit) {
                List<Transaction> transactions = transactionRepository.findTransactionsWithAssociationsByIds(transactionIds);

                for (Transaction transaction : transactions) {
                    if (processedCount >= limit) break;

                    try {
                        transactionService.processAndDetectFraud(transaction);
                        processedCount++;

                        if (processedCount % 10 == 0) {
                            log.info("사기 탐지 처리 진행: {}/{} 건 완료", processedCount, limit);
                        }

                    } catch (Exception e) {
                        log.error("거래 {} 사기 탐지 실패: {}",
                            transaction.getId(), e.getMessage());
                        markTransactionAsError(transaction);
                    }
                }

                // 다음 페이지
                if (transactionIds.size() >= PROCESSING_BATCH_SIZE && processedCount < limit) {
                    pageable = pageable.next();
                    transactionIds = transactionRepository.findPendingTransactionIds(pageable);
                } else {
                    break;
                }
            }

            log.info("완료: PENDING 상태 거래 처리 - {} 건 완료", processedCount);

        } catch (Exception e) {
            log.error("제한된 배치 사기 탐지 처리 중 오류 발생", e);
        }

        return CompletableFuture.completedFuture(processedCount);
    }

    /**
     * PENDING 상태 거래 개수 조회 (Redis 캐시 적용 + Fallback)
     */
    @Cacheable(value = "pendingCount", key = "'pending_transaction_count'")
    public long getPendingTransactionCount() {
        try {
            log.debug("데이터베이스에서 PENDING 거래 개수 조회 중...");
            return transactionRepository.countByStatus(Transaction.TransactionStatus.PENDING);
        } catch (Exception e) {
            log.error("PENDING 거래 개수 조회 실패, 캐시된 값 또는 기본값 반환: {}", e.getMessage());
            // 네트워크 문제 시 캐시된 값을 유지하거나 기본값 반환
            return 0L;
        }
    }

    /**
     * PENDING 상태 거래 개수 조회 (캐시 무시하고 강제 조회)
     */
    public long getPendingTransactionCountForced() {
        try {
            return transactionRepository.countByStatus(Transaction.TransactionStatus.PENDING);
        } catch (Exception e) {
            log.error("강제 PENDING 거래 개수 조회 실패: {}", e.getMessage());
            throw new RuntimeException("데이터베이스 연결 실패: " + e.getMessage(), e);
        }
    }

    @Transactional
    protected void markTransactionAsError(Transaction transaction) {
        try {
            transaction.setStatus(Transaction.TransactionStatus.ERROR);
            transactionRepository.save(transaction);
        } catch (Exception e) {
            log.error("거래 {} 오류 상태 표시 실패: {}", transaction.getId(), e.getMessage());
        }
    }
}
package com.credit.card.fraud.detection.transactions.service;

import com.credit.card.fraud.detection.transactions.entity.Transaction;
import com.credit.card.fraud.detection.transactions.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
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

    private static final int PROCESSING_BATCH_SIZE = 500;  // 배치 크기 증가
    private static final int PARALLEL_THREADS = 4;  // 병렬 처리 스레드 수

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
                pendingTransactions = transactionRepository.findByStatus(
                    Transaction.TransactionStatus.PENDING, pageable);

                if (pendingTransactions.hasContent()) {
                    List<Transaction> transactions = pendingTransactions.getContent();

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
                }

                pageable = pageable.next();

            } while (pendingTransactions.hasNext());

            log.info("완료: PENDING 상태 거래 사기 탐지 처리 - 처리: {}, 실패: {}",
                processedCount.get(), failedCount.get());

        } catch (Exception e) {
            log.error("배치 사기 탐지 처리 중 오류 발생", e);
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        return CompletableFuture.completedFuture(null);
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
            Page<Transaction> pendingTransactions = transactionRepository.findByStatus(
                Transaction.TransactionStatus.PENDING, pageable);

            while (pendingTransactions.hasContent() && processedCount < limit) {
                List<Transaction> transactions = pendingTransactions.getContent();

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
                if (pendingTransactions.hasNext() && processedCount < limit) {
                    pageable = pageable.next();
                    pendingTransactions = transactionRepository.findByStatus(
                        Transaction.TransactionStatus.PENDING, pageable);
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
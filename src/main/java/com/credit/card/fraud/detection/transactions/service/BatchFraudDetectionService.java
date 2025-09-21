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

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchFraudDetectionService {

    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;

    private static final int PROCESSING_BATCH_SIZE = 100;

    /**
     * PENDING 상태의 거래들에 대해 비동기로 사기 탐지 수행
     */
    @Async
    @CacheEvict(value = "pendingCount", key = "'pending_transaction_count'")
    public CompletableFuture<Void> processPendingTransactions() {
        log.info("시작: PENDING 상태 거래 사기 탐지 처리");

        int processedCount = 0;
        int failedCount = 0;

        try {
            Pageable pageable = PageRequest.of(0, PROCESSING_BATCH_SIZE);
            Page<Transaction> pendingTransactions;

            do {
                pendingTransactions = transactionRepository.findByStatus(
                    Transaction.TransactionStatus.PENDING, pageable);

                if (pendingTransactions.hasContent()) {
                    List<Transaction> transactions = pendingTransactions.getContent();

                    for (Transaction transaction : transactions) {
                        try {
                            // 개별 거래에 대해 사기 탐지 수행
                            transactionService.processAndDetectFraud(transaction);
                            processedCount++;

                            if (processedCount % 50 == 0) {
                                log.info("사기 탐지 처리 진행: {} 건 완료", processedCount);
                            }

                        } catch (Exception e) {
                            failedCount++;
                            log.error("거래 {} 사기 탐지 실패: {}",
                                transaction.getId(), e.getMessage());

                            // 실패한 거래는 ERROR 상태로 표시
                            markTransactionAsError(transaction);
                        }
                    }
                }

                // 다음 페이지로
                pageable = pageable.next();

            } while (pendingTransactions.hasNext());

            log.info("완료: PENDING 상태 거래 사기 탐지 처리 - 처리: {}, 실패: {}",
                processedCount, failedCount);

        } catch (Exception e) {
            log.error("배치 사기 탐지 처리 중 오류 발생", e);
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
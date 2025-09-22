package com.credit.card.fraud.detection.transactions.service;

import com.credit.card.fraud.detection.modelclient.service.EnsembleModelService;
import com.credit.card.fraud.detection.transactions.entity.FraudDetectionResult;
import com.credit.card.fraud.detection.transactions.entity.Transaction;
import com.credit.card.fraud.detection.transactions.exceptions.TransactionNotFoundException;
import com.credit.card.fraud.detection.transactions.exceptions.TransactionProcessingException;
import com.credit.card.fraud.detection.transactions.exceptions.TransactionQueryException;
import com.credit.card.fraud.detection.transactions.repository.FraudDetectionResultRepository;
import com.credit.card.fraud.detection.transactions.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final FraudDetectionResultRepository fraudDetectionResultRepository;

    // 책임 분리된 서비스들
    private final FraudDetectionEngine fraudDetectionEngine;
    private final TransactionNotificationService notificationService;

    /**
     * 거래 처리 및 사기 탐지 수행
     */
    @Transactional
    public Transaction processAndDetectFraud(Transaction transaction) {
        validateTransaction(transaction);

        try {
            // 1. 거래 저장
            Transaction savedTransaction = transactionRepository.save(transaction);
            log.debug("Transaction {} saved successfully", savedTransaction.getId());

            // 2. 사기 탐지 실행
            FraudDetectionResult detectionResult = fraudDetectionEngine.detectFraud(savedTransaction);

            // 3. 탐지 결과에 따른 거래 상태 업데이트
            updateTransactionBasedOnDetection(savedTransaction, detectionResult);

            // 4. 알림 처리
            notificationService.handleDetectionResult(savedTransaction, detectionResult);

            log.info("Transaction {} processed: fraud_score={}, prediction={}",
                    savedTransaction.getId(), detectionResult.getFinalScore(), detectionResult.getFinalPrediction());

            return savedTransaction;

        } catch (Exception e) {
            log.error("Error processing transaction {}: {}", transaction.getId(), e.getMessage(), e);
            throw new TransactionProcessingException("Failed to process transaction: " + e.getMessage(), e);
        }
    }

    /**
     * 배치용 거래 처리 (성능 최적화)
     */
    @Transactional
    public List<Transaction> processBatchTransactions(List<Transaction> transactions) {
        if (transactions.isEmpty()) {
            return transactions;
        }

        try {
            // 1. 배치 저장
            List<Transaction> savedTransactions = transactionRepository.saveAll(transactions);

            // 2. 사기 탐지 및 결과 저장
            List<FraudDetectionResult> detectionResults = new ArrayList<>();

            for (Transaction transaction : savedTransactions) {
                try {
                    FraudDetectionResult result = fraudDetectionEngine.detectFraud(transaction);
                    detectionResults.add(result);

                    // 거래 상태 업데이트
                    if (result.getFinalPrediction()) {
                        transaction.markAsFraud();
                    }
                    transaction.markAsProcessed();
                    transaction.addDetectionResult(result);

                } catch (Exception e) {
                    log.error("거래 {} 사기 탐지 실패: {}", transaction.getId(), e.getMessage());
                    transaction.setStatus(Transaction.TransactionStatus.ERROR);
                }
            }

            // 3. 배치 저장
            fraudDetectionResultRepository.saveAll(detectionResults);
            transactionRepository.saveAll(savedTransactions);

            log.info("배치 처리 완료: {} 건", savedTransactions.size());
            return savedTransactions;

        } catch (Exception e) {
            log.error("배치 처리 실패: {}", e.getMessage(), e);
            throw new TransactionProcessingException("배치 처리 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 필터 조건으로 거래 조회
     */
    @Transactional(readOnly = true)
    public Page<Transaction> getTransactionsWithFilters(
            String userId, String merchant, String category,
            BigDecimal minAmount, BigDecimal maxAmount, Boolean isFraud,
            LocalDateTime startTime, LocalDateTime endTime, Pageable pageable) {

        try {
            return transactionRepository.findWithFilters(
                    userId, merchant, category, minAmount, maxAmount, isFraud,
                    startTime, endTime, pageable
            );
        } catch (Exception e) {
            log.error("Error retrieving transactions with filters: {}", e.getMessage(), e);
            throw new TransactionQueryException("Failed to retrieve transactions: " + e.getMessage(), e);
        }
    }

    /**
     * 거래의 사기 탐지 결과 조회
     */
    @Transactional(readOnly = true)
    public Optional<FraudDetectionResult> getFraudDetectionResult(Long transactionId) {
        validateTransactionId(transactionId);

        try {
            return fraudDetectionResultRepository.findByTransaction_Id(transactionId);
        } catch (Exception e) {
            log.error("Error retrieving fraud detection result for transaction {}: {}",
                    transactionId, e.getMessage(), e);
            throw new TransactionQueryException("Failed to retrieve fraud detection result", e);
        }
    }

    /**
     * 거래 ID로 조회
     */
    @Transactional(readOnly = true)
    public Transaction getTransactionById(Long transactionId) {
        validateTransactionId(transactionId);

        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + transactionId));
    }

    /**
     * 사용자별 거래 이력 조회
     */
    @Transactional(readOnly = true)
    public List<Transaction> getTransactionsByUserId(String userId, Pageable pageable) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }

        return transactionRepository.findByUserIdOrderByVirtualTimeDesc(userId, pageable);
    }

    /**
     * 고위험 거래 조회
     */
    @Transactional(readOnly = true)
    public List<Transaction> getHighRiskTransactions(BigDecimal minScore, Pageable pageable) {
        if (minScore == null) {
            minScore = new BigDecimal("0.7"); // 기본 고위험 임계값
        }

        return transactionRepository.findHighRiskTransactions(minScore, pageable);
    }

    /**
     * 골드 라벨이 있는 거래 조회
     */
    @Transactional(readOnly = true)
    public List<Transaction> getGoldLabelTransactions(Boolean isFraud, Pageable pageable) {
        return transactionRepository.findByGoldLabelIsNotNullAndGoldLabel(isFraud, pageable);
    }

    // Private helper methods

    private void validateTransaction(Transaction transaction) {
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction cannot be null");
        }
        if (transaction.getUserId() == null || transaction.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        if (transaction.getAmount() == null || transaction.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (transaction.getMerchant() == null || transaction.getMerchant().trim().isEmpty()) {
            throw new IllegalArgumentException("Merchant cannot be null or empty");
        }
    }

    private void validateTransactionId(Long transactionId) {
        if (transactionId == null) {
            throw new IllegalArgumentException("Transaction ID cannot be null");
        }
    }

    private void updateTransactionBasedOnDetection(Transaction transaction, FraudDetectionResult detectionResult) {
        if (detectionResult.getFinalPrediction()) {
            transaction.markAsFraud();
        }
        transaction.markAsProcessed();
        transaction.addDetectionResult(detectionResult);

        transactionRepository.save(transaction);
    }
}
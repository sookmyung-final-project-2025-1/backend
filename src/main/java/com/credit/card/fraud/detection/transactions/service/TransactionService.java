package com.credit.card.fraud.detection.transactions.service;

import com.credit.card.fraud.detection.modelclient.dto.ModelPredictionRequest;
import com.credit.card.fraud.detection.modelclient.dto.ModelPredictionResponse;
import com.credit.card.fraud.detection.modelclient.service.EnsembleModelService;
import com.credit.card.fraud.detection.transactions.entity.FraudDetectionResult;
import com.credit.card.fraud.detection.transactions.entity.Transaction;
import com.credit.card.fraud.detection.transactions.entity.UserReport;
import com.credit.card.fraud.detection.transactions.repository.FraudDetectionResultRepository;
import com.credit.card.fraud.detection.transactions.repository.TransactionRepository;
import com.credit.card.fraud.detection.transactions.repository.UserReportRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {
    
    private final TransactionRepository transactionRepository;
    private final FraudDetectionResultRepository fraudDetectionResultRepository;
    private final UserReportRepository userReportRepository;
    private final EnsembleModelService ensembleModelService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    
    @Transactional
    public Transaction processAndDetectFraud(Transaction transaction) {
        // 1. 트랜잭션 저장
        Transaction savedTransaction = transactionRepository.save(transaction);
        
        // 2. 사기 탐지 실행
        try {
            FraudDetectionResult detectionResult = detectFraud(savedTransaction);
            
            // 3. 탐지 결과에 따라 트랜잭션 상태 업데이트
            if (detectionResult.getFinalPrediction()) {
                savedTransaction.markAsFraud();
                transactionRepository.save(savedTransaction);
            }
            
            // 4. 고위험 거래 실시간 알림
            if (detectionResult.getFinalScore().compareTo(new BigDecimal("0.8")) > 0) {
                sendHighRiskAlert(savedTransaction, detectionResult);
            }
            
            // 5. 웹소켓으로 실시간 데이터 전송
            sendRealtimeUpdate(savedTransaction, detectionResult);
            
            log.debug("Transaction {} processed with fraud score: {}", 
                savedTransaction.getId(), detectionResult.getFinalScore());
            
        } catch (Exception e) {
            log.error("Error during fraud detection for transaction {}: {}", 
                savedTransaction.getId(), e.getMessage());
        }
        
        return savedTransaction;
    }
    
    @Transactional
    public FraudDetectionResult detectFraud(Transaction transaction) {
        // 모델 예측 요청 생성
        ModelPredictionRequest request = buildPredictionRequest(transaction);
        
        // 앙상블 모델로 예측 실행
        ModelPredictionResponse response = ensembleModelService.predict(request);
        
        if (!response.getSuccess()) {
            throw new RuntimeException("Model prediction failed: " + response.getErrorMessage());
        }
        
        // 예측 결과를 DB에 저장
        FraudDetectionResult detectionResult = FraudDetectionResult.builder()
            .transaction(transaction)
            .lgbmScore(response.getLgbmScore())
            .xgboostScore(response.getXgboostScore())
            .catboostScore(response.getCatboostScore())
            .finalScore(response.getFinalScore())
            .finalPrediction(response.getFinalPrediction())
            .confidenceScore(response.getConfidenceScore())
            .lgbmWeight(response.getLgbmWeight())
            .xgboostWeight(response.getXgboostWeight())
            .catboostWeight(response.getCatboostWeight())
            .threshold(response.getThreshold())
            .predictionTime(response.getPredictionTime())
            .processingTimeMs(response.getProcessingTimeMs())
            .modelVersion(response.getModelVersion())
            .build();
        
        // 피처 중요도와 어텐션 스코어를 JSON으로 저장
        try {
            if (response.getFeatureImportance() != null) {
                detectionResult.setFeatureImportance(
                    objectMapper.writeValueAsString(response.getFeatureImportance()));
            }
            if (response.getAttentionScores() != null) {
                detectionResult.setAttentionScores(
                    objectMapper.writeValueAsString(response.getAttentionScores()));
            }
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize feature importance or attention scores: {}", e.getMessage());
        }
        
        return fraudDetectionResultRepository.save(detectionResult);
    }
    
    private ModelPredictionRequest buildPredictionRequest(Transaction transaction) {
        // 익명화된 피처들 시뮬레이션 (실제 환경에서는 실제 피처값 사용)
        Map<String, Object> anonymizedFeatures = generateAnonymizedFeatures();
        
        return ModelPredictionRequest.builder()
            .transactionId(transaction.getId())
            .userId(transaction.getUserId())
            .amount(transaction.getAmount())
            .merchant(transaction.getMerchant())
            .merchantCategory(transaction.getMerchantCategory())
            .transactionTime(transaction.getTransactionTime())
            .latitude(transaction.getLatitude())
            .longitude(transaction.getLongitude())
            .deviceFingerprint(transaction.getDeviceFingerprint())
            .ipAddress(transaction.getIpAddress())
            .anonymizedFeatures(anonymizedFeatures)
            .modelWeights(ensembleModelService.getCurrentWeights())
            .threshold(ensembleModelService.getCurrentThreshold())
            .build();
    }
    
    private Map<String, Object> generateAnonymizedFeatures() {
        Map<String, Object> features = new HashMap<>();
        
        // C1~C28 등의 익명화된 피처들 시뮬레이션
        for (int i = 1; i <= 28; i++) {
            features.put("C" + i, Math.random() * 100);
        }
        
        return features;
    }
    
    @Transactional
    public UserReport reportFraud(Long transactionId, String reportedBy, String reason, String description) {
        Optional<Transaction> transactionOpt = transactionRepository.findById(transactionId);
        if (transactionOpt.isEmpty()) {
            throw new IllegalArgumentException("Transaction not found: " + transactionId);
        }
        
        UserReport report = UserReport.builder()
            .transaction(transactionOpt.get())
            .reportedBy(reportedBy)
            .reason(reason)
            .description(description)
            .build();
        
        UserReport savedReport = userReportRepository.save(report);
        
        log.info("Fraud report submitted for transaction {} by user {}", transactionId, reportedBy);
        
        // 관리자에게 신고 알림
        sendReportNotification(savedReport);
        
        return savedReport;
    }
    
    @Transactional
    public void approveReport(Long reportId, String reviewedBy, String comment, Boolean isFraud) {
        Optional<UserReport> reportOpt = userReportRepository.findById(reportId);
        if (reportOpt.isEmpty()) {
            throw new IllegalArgumentException("Report not found: " + reportId);
        }
        
        UserReport report = reportOpt.get();
        report.approve(reviewedBy, comment, isFraud);
        userReportRepository.save(report);
        
        log.info("Report {} approved by {}: isFraud={}", reportId, reviewedBy, isFraud);
        
        // 골드 라벨이 업데이트되면 모델 재학습 트리거 (시뮬레이션)
        if (isFraud != null) {
            triggerModelUpdate(report.getTransaction());
        }
    }
    
    private void triggerModelUpdate(Transaction transaction) {
        // 실제 환경에서는 여기서 모델 재학습을 시작
        // 현재는 시뮬레이션만 수행
        log.info("Model update triggered for gold label on transaction {}", transaction.getId());
        
        // 시뮬레이션된 confidence score 변화 알림
        sendModelUpdateNotification(transaction);
    }
    
    private void sendHighRiskAlert(Transaction transaction, FraudDetectionResult detectionResult) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("type", "HIGH_RISK_TRANSACTION");
        alert.put("transactionId", transaction.getId());
        alert.put("userId", transaction.getUserId());
        alert.put("amount", transaction.getAmount());
        alert.put("merchant", transaction.getMerchant());
        alert.put("fraudScore", detectionResult.getFinalScore());
        alert.put("timestamp", LocalDateTime.now());
        
        messagingTemplate.convertAndSend("/topic/alerts", alert);
    }
    
    private void sendRealtimeUpdate(Transaction transaction, FraudDetectionResult detectionResult) {
        Map<String, Object> update = new HashMap<>();
        update.put("type", "TRANSACTION_PROCESSED");
        update.put("transaction", transaction);
        update.put("detectionResult", detectionResult);
        update.put("timestamp", LocalDateTime.now());
        
        messagingTemplate.convertAndSend("/topic/transactions", update);
    }
    
    private void sendReportNotification(UserReport report) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "NEW_FRAUD_REPORT");
        notification.put("reportId", report.getId());
        notification.put("transactionId", report.getTransaction().getId());
        notification.put("reportedBy", report.getReportedBy());
        notification.put("reason", report.getReason());
        notification.put("timestamp", LocalDateTime.now());
        
        messagingTemplate.convertAndSend("/topic/admin/reports", notification);
    }
    
    private void sendModelUpdateNotification(Transaction transaction) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "MODEL_UPDATED");
        notification.put("transactionId", transaction.getId());
        notification.put("message", "Model updated with new gold label");
        notification.put("timestamp", LocalDateTime.now());
        
        messagingTemplate.convertAndSend("/topic/model-updates", notification);
    }
    
    public List<Transaction> getTransactionsWithFilters(
            String userId, String merchant, String category,
            BigDecimal minAmount, BigDecimal maxAmount, Boolean isFraud,
            LocalDateTime startTime, LocalDateTime endTime,
            org.springframework.data.domain.Pageable pageable) {
        
        return transactionRepository.findWithFilters(
            userId, merchant, category, minAmount, maxAmount, isFraud,
            startTime, endTime, pageable
        ).getContent();
    }
    
    public Optional<FraudDetectionResult> getFraudDetectionResult(Long transactionId) {
        return fraudDetectionResultRepository.findByTransaction_Id(transactionId);
    }
}
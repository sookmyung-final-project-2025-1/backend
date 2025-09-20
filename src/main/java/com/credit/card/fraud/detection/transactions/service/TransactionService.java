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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final Random random = new Random();
    
    private final TransactionRepository transactionRepository;
    private final FraudDetectionResultRepository fraudDetectionResultRepository;
    private final UserReportRepository userReportRepository;
    private final EnsembleModelService ensembleModelService;
    private final ObjectMapper objectMapper;
    
    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;
    
    @Transactional
    public Transaction processAndDetectFraud(Transaction transaction) {
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction cannot be null");
        }
        
        try {
            // 1. 트랜잭션 저장
            Transaction savedTransaction = transactionRepository.save(transaction);
            
            // 2. 사기 탐지 실행
            FraudDetectionResult detectionResult = detectFraud(savedTransaction);
            
            // 3. 탐지 결과에 따라 트랜잭션 상태 업데이트
            if (detectionResult.getFinalPrediction()) {
                savedTransaction.markAsFraud();
                savedTransaction = transactionRepository.save(savedTransaction);
            }
            
            // 4. 고위험 거래 실시간 알림
            if (detectionResult.getFinalScore().compareTo(new BigDecimal("0.8")) > 0) {
                sendHighRiskAlert(savedTransaction, detectionResult);
            }
            
            // 5. 웹소켓으로 실시간 데이터 전송
            sendRealtimeUpdate(savedTransaction, detectionResult);
            
            log.debug("Transaction {} processed with fraud score: {}", 
                savedTransaction.getId(), detectionResult.getFinalScore());
            
            return savedTransaction;
            
        } catch (Exception e) {
            log.error("Error during fraud detection for transaction {}: {}", 
                transaction.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to process transaction: " + e.getMessage(), e);
        }
    }
    
    @Transactional
    public FraudDetectionResult detectFraud(Transaction transaction) {
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction cannot be null");
        }
        
        try {
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
            serializeModelOutputs(response, detectionResult);
            
            return fraudDetectionResultRepository.save(detectionResult);
            
        } catch (Exception e) {
            log.error("Error detecting fraud for transaction {}: {}", 
                transaction.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to detect fraud: " + e.getMessage(), e);
        }
    }
    
    private void serializeModelOutputs(ModelPredictionResponse response, FraudDetectionResult detectionResult) {
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
            log.warn("Failed to serialize feature importance or attention scores for transaction {}: {}", 
                detectionResult.getTransaction().getId(), e.getMessage());
        }
    }

    private ModelPredictionRequest buildPredictionRequest(Transaction transaction) {
        try {
            // Transaction의 anonymizedFeatures에서 실제 IEEE 데이터 추출
            Map<String, Object> ieeeFeatures = extractIEEEFeatures(transaction);

            return ModelPredictionRequest.builder()
                    .transactionId(transaction.getId())
                    .amount(transaction.getAmount())

                    // IEEE 기본 필드들 (실제 데이터 우선, 없으면 기본값)
                    .productCode(getStringValue(ieeeFeatures, "ProductCD", "W"))
                    .card1(getStringValue(ieeeFeatures, "card1", "13553"))
                    .card2(getStringValue(ieeeFeatures, "card2", "150.0"))
                    .card3(getStringValue(ieeeFeatures, "card3", "150.0"))
                    .addr1(getBigDecimalValue(ieeeFeatures, "addr1"))
                    .addr2(getBigDecimalValue(ieeeFeatures, "addr2"))
                    .dist1(getBigDecimalValue(ieeeFeatures, "dist1"))
                    .purchaserEmailDomain(getStringValue(ieeeFeatures, "P_emaildomain", "gmail.com"))

                    // IEEE 피처 맵들 (실제 데이터에서 추출)
                    .countingFeatures(extractFeatureMap(ieeeFeatures, "C", 1, 14))
                    .timeDeltas(extractFeatureMap(ieeeFeatures, "D", 1, 15))
                    .matchFeatures(extractMatchFeatures(ieeeFeatures))
                    .vestaFeatures(extractFeatureMap(ieeeFeatures, "V", 1, 339))
                    .identityFeatures(extractFeatureMap(ieeeFeatures, "id_", 1, 38))

                    // 모델 설정
                    .modelWeights(ensembleModelService.getCurrentWeights())
                    .threshold(ensembleModelService.getCurrentThreshold())
                    .build();

        } catch (Exception e) {
            log.error("Error building prediction request for transaction {}: {}",
                    transaction.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to build prediction request: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractIEEEFeatures(Transaction transaction) {
        if (transaction.getAnonymizedFeatures() == null || transaction.getAnonymizedFeatures().trim().isEmpty()) {
            return new HashMap<>();
        }

        try {
            return objectMapper.readValue(transaction.getAnonymizedFeatures(), Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse IEEE features from transaction {}: {}",
                    transaction.getId(), e.getMessage());
            return new HashMap<>();
        }
    }

    private String getStringValue(Map<String, Object> features, String key, String defaultValue) {
        Object value = features.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private BigDecimal getBigDecimalValue(Map<String, Object> features, String key) {
        Object value = features.get(key);
        if (value == null) return null;

        try {
            if (value instanceof Number) {
                return BigDecimal.valueOf(((Number) value).doubleValue());
            }
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, BigDecimal> extractFeatureMap(Map<String, Object> features, String prefix, int start, int end) {
        Map<String, BigDecimal> result = new HashMap<>();

        for (int i = start; i <= end; i++) {
            String key = prefix.equals("id_") ?
                    String.format("%s%02d", prefix, i) :
                    prefix + i;

            BigDecimal value = getBigDecimalValue(features, key);
            if (value != null) {
                result.put(key, value);
            }
        }

        return result;
    }

    private Map<String, String> extractMatchFeatures(Map<String, Object> features) {
        Map<String, String> result = new HashMap<>();

        for (int i = 1; i <= 9; i++) {
            String key = "M" + i;
            String value = getStringValue(features, key, null);
            if (value != null && (value.equals("T") || value.equals("F"))) {
                result.put(key, value);
            }
        }

        return result;
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            log.debug("Failed to parse decimal value: {}", value);
            return null;
        }
    }
    
    @Transactional
    public UserReport reportFraud(Long transactionId, String reportedBy, String reason, String description) {
        if (transactionId == null || reportedBy == null || reason == null) {
            throw new IllegalArgumentException("Transaction ID, reporter, and reason cannot be null");
        }
        
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
        if (reportId == null || reviewedBy == null) {
            throw new IllegalArgumentException("Report ID and reviewer cannot be null");
        }
        
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
        
        // 트랜잭션에 골드 라벨 설정
        transaction.setGoldLabel(transaction.getIsFraud());
        transactionRepository.save(transaction);
        
        // 시뮬레이션된 confidence score 변화 알림
        sendModelUpdateNotification(transaction);
    }
    
    private void sendHighRiskAlert(Transaction transaction, FraudDetectionResult detectionResult) {
        if (messagingTemplate == null) {
            log.debug("WebSocket messaging not available, skipping high risk alert");
            return;
        }
        
        try {
            Map<String, Object> alert = new HashMap<>();
            alert.put("type", "HIGH_RISK_TRANSACTION");
            alert.put("transactionId", transaction.getId());
            alert.put("userId", transaction.getUserId());
            alert.put("amount", transaction.getAmount());
            alert.put("merchant", transaction.getMerchant());
            alert.put("fraudScore", detectionResult.getFinalScore());
            alert.put("timestamp", LocalDateTime.now());
            
            messagingTemplate.convertAndSend("/topic/alerts", alert);
            log.debug("High risk alert sent for transaction {}", transaction.getId());
        } catch (Exception e) {
            log.warn("Failed to send high risk alert for transaction {}: {}", 
                transaction.getId(), e.getMessage());
        }
    }
    
    private void sendRealtimeUpdate(Transaction transaction, FraudDetectionResult detectionResult) {
        if (messagingTemplate == null) {
            log.debug("WebSocket messaging not available, skipping realtime update");
            return;
        }
        
        try {
            Map<String, Object> update = new HashMap<>();
            update.put("type", "TRANSACTION_PROCESSED");
            update.put("transaction", transaction);
            update.put("detectionResult", detectionResult);
            update.put("timestamp", LocalDateTime.now());
            
            messagingTemplate.convertAndSend("/topic/transactions", update);
            log.debug("Realtime update sent for transaction {}", transaction.getId());
        } catch (Exception e) {
            log.warn("Failed to send realtime update for transaction {}: {}", 
                transaction.getId(), e.getMessage());
        }
    }
    
    private void sendReportNotification(UserReport report) {
        if (messagingTemplate == null) {
            log.debug("WebSocket messaging not available, skipping report notification");
            return;
        }
        
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "NEW_FRAUD_REPORT");
            notification.put("reportId", report.getId());
            notification.put("transactionId", report.getTransaction().getId());
            notification.put("reportedBy", report.getReportedBy());
            notification.put("reason", report.getReason());
            notification.put("timestamp", LocalDateTime.now());
            
            messagingTemplate.convertAndSend("/topic/admin/reports", notification);
            log.debug("Report notification sent for report {}", report.getId());
        } catch (Exception e) {
            log.warn("Failed to send report notification for report {}: {}", 
                report.getId(), e.getMessage());
        }
    }
    
    private void sendModelUpdateNotification(Transaction transaction) {
        if (messagingTemplate == null) {
            log.debug("WebSocket messaging not available, skipping model update notification");
            return;
        }
        
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "MODEL_UPDATED");
            notification.put("transactionId", transaction.getId());
            notification.put("message", "Model updated with new gold label");
            notification.put("timestamp", LocalDateTime.now());
            
            messagingTemplate.convertAndSend("/topic/model-updates", notification);
            log.debug("Model update notification sent for transaction {}", transaction.getId());
        } catch (Exception e) {
            log.warn("Failed to send model update notification for transaction {}: {}", 
                transaction.getId(), e.getMessage());
        }
    }
    
    public List<Transaction> getTransactionsWithFilters(
            String userId, String merchant, String category,
            BigDecimal minAmount, BigDecimal maxAmount, Boolean isFraud,
            LocalDateTime startTime, LocalDateTime endTime,
            org.springframework.data.domain.Pageable pageable) {
        
        try {
            return transactionRepository.findWithFilters(
                userId, merchant, category, minAmount, maxAmount, isFraud,
                startTime, endTime, pageable
            ).getContent();
        } catch (Exception e) {
            log.error("Error retrieving transactions with filters: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve transactions: " + e.getMessage(), e);
        }
    }
    
    public Optional<FraudDetectionResult> getFraudDetectionResult(Long transactionId) {
        if (transactionId == null) {
            throw new IllegalArgumentException("Transaction ID cannot be null");
        }
        
        try {
            return fraudDetectionResultRepository.findByTransaction_Id(transactionId);
        } catch (Exception e) {
            log.error("Error retrieving fraud detection result for transaction {}: {}", 
                transactionId, e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve fraud detection result: " + e.getMessage(), e);
        }
    }
}
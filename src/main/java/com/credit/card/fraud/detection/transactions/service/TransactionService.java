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
        // IEEE 데이터셋에서 실제 피처값 추출 또는 시뮬레이션
        Map<String, String> ieeeData = parseIEEEFeaturesFromTransaction(transaction);

        return ModelPredictionRequest.builder()
            // IEEE 기본 거래 정보
            .transactionId(transaction.getId())
            .transactionDT(new BigDecimal(ieeeData.getOrDefault("TransactionDT", "0")))
            .amount(transaction.getAmount())

            // IEEE 상품 및 결제 정보
            .productCode(ieeeData.getOrDefault("ProductCD", "W"))
            .card1(ieeeData.getOrDefault("card1", "13553"))
            .card2(ieeeData.getOrDefault("card2", "150.0"))
            .card3(ieeeData.getOrDefault("card3", "150.0"))
            .card4(parseDecimal(ieeeData.get("card4")))
            .card5(ieeeData.getOrDefault("card5", "226"))
            .card6(parseDecimal(ieeeData.get("card6")))

            // IEEE 주소 및 거리 정보
            .addr1(parseDecimal(ieeeData.get("addr1")))
            .addr2(parseDecimal(ieeeData.get("addr2")))
            .dist1(parseDecimal(ieeeData.get("dist1")))
            .dist2(parseDecimal(ieeeData.get("dist2")))

            // IEEE 이메일 도메인
            .purchaserEmailDomain(ieeeData.getOrDefault("P_emaildomain", "gmail.com"))
            .recipientEmailDomain(ieeeData.get("R_emaildomain"))

            // IEEE 피처 맵들
            .countingFeatures(generateCountingFeatures(ieeeData))
            .timeDeltas(generateTimeDeltas(ieeeData))
            .matchFeatures(generateMatchFeatures(ieeeData))
            .vestaFeatures(generateVestaFeatures(ieeeData))
            .identityFeatures(generateIdentityFeatures(ieeeData))

            // IEEE 디바이스 정보
            .deviceType(ieeeData.getOrDefault("DeviceType", "desktop"))
            .deviceInfo(ieeeData.getOrDefault("DeviceInfo", "Windows"))

            // 백엔드 생성 필드 (IEEE에서 파생)
            .userId(generateUserIdFromTransaction(transaction))
            .merchant(generateMerchantFromProduct(ieeeData.getOrDefault("ProductCD", "W")))
            .merchantCategory(generateCategoryFromProduct(ieeeData.getOrDefault("ProductCD", "W")))
            .transactionTime(transaction.getTransactionTime())
            .latitude(transaction.getLatitude())
            .longitude(transaction.getLongitude())
            .deviceFingerprint(generateDeviceFingerprint(ieeeData))
            .ipAddress(generateIpFromIdentity(ieeeData))


            // 모델 설정
            .modelWeights(ensembleModelService.getCurrentWeights())
            .threshold(ensembleModelService.getCurrentThreshold())
            .build();
    }
    
    // IEEE 데이터 파싱 및 생성 메서드들
    private Map<String, String> parseIEEEFeaturesFromTransaction(Transaction transaction) {
        Map<String, String> ieeeData = new HashMap<>();

        // Transaction.anonymizedFeatures에서 IEEE 데이터 파싱
        if (transaction.getAnonymizedFeatures() != null) {
            try {
                Map<String, Object> features = objectMapper.readValue(
                    transaction.getAnonymizedFeatures(), Map.class);

                // IEEE 필드들을 String으로 변환하여 저장
                features.forEach((key, value) -> {
                    if (value != null) {
                        ieeeData.put(key, value.toString());
                    }
                });
            } catch (Exception e) {
                log.warn("Failed to parse IEEE features from transaction {}: {}",
                    transaction.getId(), e.getMessage());
            }
        }

        // 기본값 설정 (실제 환경에서는 IEEE CSV에서 읽어옴)
        ieeeData.putIfAbsent("TransactionDT", String.valueOf(System.currentTimeMillis() / 1000));
        ieeeData.putIfAbsent("ProductCD", getRandomProductCode());
        ieeeData.putIfAbsent("card1", getRandomCard1());
        ieeeData.putIfAbsent("DeviceType", getRandomDeviceType());

        return ieeeData;
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, BigDecimal> generateCountingFeatures(Map<String, String> ieeeData) {
        Map<String, BigDecimal> features = new HashMap<>();

        // C1-C14 생성 (실제 환경에서는 IEEE CSV에서 읽어옴)
        for (int i = 1; i <= 14; i++) {
            String key = "C" + i;
            String value = ieeeData.get(key);
            if (value != null) {
                features.put(key, parseDecimal(value));
            } else {
                // 시뮬레이션된 값
                features.put(key, BigDecimal.valueOf(random.nextDouble() * 100)
                    .setScale(2, RoundingMode.HALF_UP));
            }
        }

        return features;
    }

    private Map<String, BigDecimal> generateTimeDeltas(Map<String, String> ieeeData) {
        Map<String, BigDecimal> features = new HashMap<>();

        // D1-D15 생성
        for (int i = 1; i <= 15; i++) {
            String key = "D" + i;
            String value = ieeeData.get(key);
            if (value != null) {
                features.put(key, parseDecimal(value));
            } else {
                // 시뮬레이션된 시간 델타 (일 단위)
                features.put(key, BigDecimal.valueOf(random.nextDouble() * 365)
                    .setScale(2, RoundingMode.HALF_UP));
            }
        }

        return features;
    }

    private Map<String, String> generateMatchFeatures(Map<String, String> ieeeData) {
        Map<String, String> features = new HashMap<>();

        // M1-M9 생성 (T/F 값)
        for (int i = 1; i <= 9; i++) {
            String key = "M" + i;
            String value = ieeeData.get(key);
            if (value != null) {
                features.put(key, value);
            } else {
                // 시뮬레이션된 매치 값
                features.put(key, random.nextBoolean() ? "T" : "F");
            }
        }

        return features;
    }

    private Map<String, BigDecimal> generateVestaFeatures(Map<String, String> ieeeData) {
        Map<String, BigDecimal> features = new HashMap<>();

        // V1-V339 중 주요한 피처들만 생성
        int[] importantV = {1, 2, 3, 4, 6, 8, 11, 12, 13, 17, 19, 20, 29, 30, 33, 34, 35, 36, 37, 38,
                           44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63,
                           64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83};

        for (int i : importantV) {
            String key = "V" + i;
            String value = ieeeData.get(key);
            if (value != null) {
                features.put(key, parseDecimal(value));
            } else {
                // 시뮬레이션된 Vesta 피처
                features.put(key, BigDecimal.valueOf(random.nextDouble() * 10)
                    .setScale(4, RoundingMode.HALF_UP));
            }
        }

        return features;
    }

    private Map<String, BigDecimal> generateIdentityFeatures(Map<String, String> ieeeData) {
        Map<String, BigDecimal> features = new HashMap<>();

        // id_01-id_38 중 주요한 피처들만 생성
        for (int i = 1; i <= 38; i++) {
            String key = "id_" + String.format("%02d", i);
            String value = ieeeData.get(key);
            if (value != null) {
                features.put(key, parseDecimal(value));
            } else if (i <= 20) { // 주요한 identity 피처들만
                // 시뮬레이션된 identity 피처
                features.put(key, BigDecimal.valueOf(random.nextDouble() * 1000)
                    .setScale(2, RoundingMode.HALF_UP));
            }
        }

        return features;
    }

    // 백엔드 생성 필드 메서드들
    private String generateUserIdFromTransaction(Transaction transaction) {
        // IEEE에는 UserID가 없으므로 TransactionID 기반으로 생성
        return "user_" + (transaction.getId() % 1000000);
    }

    private String generateMerchantFromProduct(String productCode) {
        // ProductCD 기반으로 가맹점명 생성
        switch (productCode) {
            case "W": return "WorldWide_Store";
            case "C": return "Card_Services";
            case "H": return "Home_Shopping";
            case "S": return "Service_Provider";
            case "R": return "Retail_Chain";
            default: return "Unknown_Merchant";
        }
    }

    private String generateCategoryFromProduct(String productCode) {
        // ProductCD 기반으로 카테고리 생성
        switch (productCode) {
            case "W": return "ONLINE_RETAIL";
            case "C": return "FINANCIAL_SERVICES";
            case "H": return "HOME_GARDEN";
            case "S": return "SERVICES";
            case "R": return "RETAIL";
            default: return "OTHER";
        }
    }

    private String generateDeviceFingerprint(Map<String, String> ieeeData) {
        String deviceType = ieeeData.getOrDefault("DeviceType", "desktop");
        String deviceInfo = ieeeData.getOrDefault("DeviceInfo", "Windows");
        return deviceType + "_" + deviceInfo.hashCode();
    }

    private String generateIpFromIdentity(Map<String, String> ieeeData) {
        // Identity 피처 기반으로 IP 생성 (시뮬레이션)
        String id_31 = ieeeData.get("id_31");
        if (id_31 != null) {
            return "192.168." + (id_31.hashCode() % 256) + "." + (id_31.hashCode() / 256 % 256);
        }
        return "192.168.1." + (random.nextInt(254) + 1);
    }


    // 랜덤 값 생성 헬퍼 메서드들
    private String getRandomProductCode() {
        String[] codes = {"W", "C", "H", "S", "R"};
        return codes[random.nextInt(codes.length)];
    }

    private String getRandomCard1() {
        return String.valueOf(10000 + random.nextInt(90000));
    }

    private String getRandomDeviceType() {
        String[] types = {"desktop", "mobile"};
        return types[random.nextInt(types.length)];
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
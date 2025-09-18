package com.credit.card.fraud.detection.transactions.service;

import com.credit.card.fraud.detection.transactions.dto.StreamingConfig;
import com.credit.card.fraud.detection.transactions.entity.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionStreamingService {
    
    private final TransactionService transactionService;
    
    // 웹소켓 메시징 템플릿 (선택적 의존성)
    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;
    
    private final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(2, 
        r -> {
            Thread t = new Thread(r, "transaction-streaming");
            t.setDaemon(true);
            return t;
        });
    
    private final AtomicReference<StreamingConfig> currentConfig = new AtomicReference<>();
    private final AtomicReference<LocalDateTime> currentVirtualTime = new AtomicReference<>();
    private final AtomicBoolean isStreaming = new AtomicBoolean(false);
    private final Map<String, Object> streamingStatus = new ConcurrentHashMap<>();
    
    // 2017년 1월 1일을 기본 시작점으로 설정 (실제 데이터셋의 시작점)
    private static final LocalDateTime DEFAULT_START_TIME = LocalDateTime.of(2017, 1, 1, 0, 0);
    private static final LocalDateTime DATA_END_TIME = LocalDateTime.of(2019, 12, 31, 23, 59);
    
    public void startRealTimeStreaming() {
        startStreaming(StreamingConfig.builder()
            .mode(StreamingConfig.StreamingMode.REALTIME)
            .speedMultiplier(1.0)
            .build());
    }
    
    public void startTimeMachineStreaming(LocalDateTime startTime, Double speedMultiplier) {
        if (startTime == null) {
            startTime = DEFAULT_START_TIME;
        }
        
        startStreaming(StreamingConfig.builder()
            .mode(StreamingConfig.StreamingMode.TIMEMACHINE)
            .startTime(startTime)
            .endTime(DATA_END_TIME)
            .speedMultiplier(speedMultiplier != null ? speedMultiplier : 1.0)
            .build());
    }
    
    @Async
    public void startStreaming(StreamingConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Streaming config cannot be null");
        }
        
        if (isStreaming.get()) {
            log.warn("Streaming is already running. Stop current streaming first.");
            return;
        }
        
        try {
            currentConfig.set(config);
            isStreaming.set(true);
            
            // 시작 시간 설정
            LocalDateTime startTime = config.getMode() == StreamingConfig.StreamingMode.REALTIME 
                ? DEFAULT_START_TIME 
                : config.getStartTime();
            
            currentVirtualTime.set(startTime);
            
            log.info("Starting transaction streaming - Mode: {}, Speed: {}x, Start: {}", 
                config.getMode(), config.getSpeedMultiplier(), startTime);
            
            updateStreamingStatus();
            
            // 스케줄러로 주기적 실행
            scheduler.scheduleAtFixedRate(
                this::processNextBatch, 
                0, 
                config.getAdjustedIntervalMs(), 
                TimeUnit.MILLISECONDS
            );
        } catch (Exception e) {
            log.error("Failed to start streaming: {}", e.getMessage(), e);
            isStreaming.set(false);
            throw new RuntimeException("Failed to start streaming: " + e.getMessage(), e);
        }
    }
    
    private void processNextBatch() {
        if (!isStreaming.get()) {
            return;
        }
        
        StreamingConfig config = currentConfig.get();
        if (config == null || config.getPaused()) {
            return;
        }
        
        try {
            LocalDateTime virtualTime = currentVirtualTime.get();
            
            // 종료 조건 체크
            if (config.getEndTime() != null && virtualTime.isAfter(config.getEndTime())) {
                stopStreaming();
                return;
            }
            
            // 다음 시간 윈도우 계산
            LocalDateTime nextVirtualTime = config.getNextVirtualTime(virtualTime);
            
            // 해당 시간 윈도우의 트랜잭션들 조회 및 처리
            List<Transaction> transactions = generateTransactionsForTimeWindow(virtualTime, nextVirtualTime);
            
            if (!transactions.isEmpty()) {
                // 각 트랜잭션에 대해 사기 탐지 실행
                transactions.forEach(this::processTransaction);
                
                log.debug("Processed {} transactions for time window {} - {}", 
                    transactions.size(), virtualTime, nextVirtualTime);
            }
            
            // 가상 시간 업데이트
            currentVirtualTime.set(nextVirtualTime);
            updateStreamingStatus();
            
            // 웹소켓으로 상태 전송
            broadcastStreamingStatus();
            
        } catch (Exception e) {
            log.error("Error processing transaction batch", e);
        }
    }
    
    private List<Transaction> generateTransactionsForTimeWindow(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            return Collections.emptyList();
        }
        
        // 실제 운영 환경에서는 IEEE 데이터셋에서 해당 시간대 데이터를 조회
        // 2017~2019 데이터를 "현재처럼" 보이게 시간축 이동하여 실시간 스트리밍 시뮬레이션

        List<Transaction> transactions = new ArrayList<>();
        Random random = new Random();

        try {
            // 시간대별 트랜잭션 패턴 시뮬레이션 (현실적인 패턴)
            int baseCount = getBaseTransactionCountForHour(startTime.getHour());
            int transactionCount = Math.max(0, (int) (baseCount * (0.8 + random.nextDouble() * 0.4))); // ±20% 변동

            for (int i = 0; i < transactionCount; i++) {
                LocalDateTime transactionTime = startTime.plusMinutes(random.nextInt(60));

                // IEEE 데이터셋 스타일의 트랜잭션 생성
                Transaction transaction = Transaction.builder()
                    .userId("USER_" + (10000 + random.nextInt(90000)))
                    .amount(generateRealisticAmount(random))
                    .merchant(generateMerchantFromProduct(getRandomProductCode()))
                    .merchantCategory(getRandomMerchantCategory())
                    .transactionTime(transactionTime)
                    .virtualTime(transactionTime) // 시연용이므로 동일하게 설정
                    .latitude(generateRandomLatitude(random))
                    .longitude(generateRandomLongitude(random))
                    .deviceFingerprint(generateRandomDeviceFingerprint(random))
                    .ipAddress(generateRandomIP())
                    .externalTransactionId(UUID.randomUUID().toString())
                    .anonymizedFeatures(generateIEEEAnonymizedFeatures(random))
                    .build();

                transactions.add(transaction);
            }
        } catch (Exception e) {
            log.error("Error generating transactions for time window {} - {}: {}", 
                startTime, endTime, e.getMessage(), e);
        }

        return transactions;
    }

    // 시간대별 현실적인 트랜잭션 수 패턴
    private int getBaseTransactionCountForHour(int hour) {
        // 새벽 시간대는 적고, 낮/저녁 시간대는 많은 패턴
        if (hour >= 0 && hour < 6) return 50;      // 새벽: 50건/시간
        if (hour >= 6 && hour < 9) return 200;     // 출근시간: 200건/시간
        if (hour >= 9 && hour < 12) return 300;    // 오전: 300건/시간
        if (hour >= 12 && hour < 14) return 400;   // 점심시간: 400건/시간
        if (hour >= 14 && hour < 18) return 350;   // 오후: 350건/시간
        if (hour >= 18 && hour < 22) return 450;   // 저녁: 450건/시간
        return 150;                                // 늦은 밤: 150건/시간
    }

    private BigDecimal generateRealisticAmount(Random random) {
        // IEEE 데이터셋의 실제 금액 분포를 모방한 현실적인 금액 생성
        double logNormal = Math.exp(random.nextGaussian() * 1.5 + 3.0); // 로그정규분포
        return BigDecimal.valueOf(Math.max(1.0, Math.min(50000.0, logNormal)))
                        .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal generateRandomLatitude(Random random) {
        // 주요 도시들 주변으로 위도 분산
        double[] cities = {37.5665, 35.1796, 33.4484, 39.0392}; // 서울, 대전, 부산, 평양
        double baseLatitude = cities[random.nextInt(cities.length)];
        return BigDecimal.valueOf(baseLatitude + (random.nextGaussian() * 0.1))
                        .setScale(8, RoundingMode.HALF_UP);
    }

    private BigDecimal generateRandomLongitude(Random random) {
        // 주요 도시들 주변으로 경도 분산
        double[] cities = {126.9780, 127.3845, 129.0756, 125.7625}; // 서울, 대전, 부산, 평양
        double baseLongitude = cities[random.nextInt(cities.length)];
        return BigDecimal.valueOf(baseLongitude + (random.nextGaussian() * 0.1))
                        .setScale(8, RoundingMode.HALF_UP);
    }

    private String generateRandomDeviceFingerprint(Random random) {
        String[] deviceTypes = {"desktop", "mobile", "tablet"};
        String[] browsers = {"Chrome", "Firefox", "Safari", "Edge"};
        String[] os = {"Windows", "macOS", "Android", "iOS", "Linux"};

        return String.format("%s_%s_%s_%d",
            deviceTypes[random.nextInt(deviceTypes.length)],
            browsers[random.nextInt(browsers.length)],
            os[random.nextInt(os.length)],
            random.nextInt(10000)
        );
    }

    private String generateIEEEAnonymizedFeatures(Random random) {
        // IEEE 데이터셋 스타일의 익명화된 피처들을 JSON으로 생성
        try {
            Map<String, Object> features = new HashMap<>();

            // IEEE TransactionDT (시간 델타)
            features.put("TransactionDT", System.currentTimeMillis() / 1000);

            // IEEE ProductCD
            features.put("ProductCD", getRandomProductCode());

            // IEEE 카드 정보
            features.put("card1", String.valueOf(10000 + random.nextInt(90000)));
            features.put("card2", String.valueOf(100 + random.nextInt(400)));
            features.put("card3", String.valueOf(100 + random.nextInt(400)));
            features.put("card4", random.nextDouble() * 1000);
            features.put("card5", String.valueOf(100 + random.nextInt(400)));
            features.put("card6", random.nextDouble() * 100);

            // IEEE 주소 정보
            features.put("addr1", random.nextDouble() * 1000);
            features.put("addr2", random.nextDouble() * 1000);
            features.put("dist1", random.nextDouble() * 10000);
            features.put("dist2", random.nextDouble() * 10000);

            // IEEE 이메일 도메인
            String[] emailDomains = {"gmail.com", "yahoo.com", "outlook.com", "tempmail.com", "guerrillamail.com"};
            features.put("P_emaildomain", emailDomains[random.nextInt(emailDomains.length)]);
            if (random.nextBoolean()) {
                features.put("R_emaildomain", emailDomains[random.nextInt(emailDomains.length)]);
            }

            // IEEE C1-C14 (카운팅 피처)
            for (int i = 1; i <= 14; i++) {
                features.put("C" + i, random.nextDouble() * 100);
            }

            // IEEE D1-D15 (시간 델타 피처)
            for (int i = 1; i <= 15; i++) {
                if (random.nextDouble() > 0.3) { // 70% 확률로 값 존재
                    features.put("D" + i, random.nextDouble() * 365);
                }
            }

            // IEEE M1-M9 (매치 피처)
            for (int i = 1; i <= 9; i++) {
                if (random.nextDouble() > 0.2) { // 80% 확률로 값 존재
                    features.put("M" + i, random.nextBoolean() ? "T" : "F");
                }
            }

            // IEEE V1-V339 중 주요한 것들만
            int[] importantV = {1, 2, 3, 4, 6, 8, 11, 12, 13, 17, 19, 20, 29, 30, 33, 34, 35, 36, 37, 38};
            for (int v : importantV) {
                if (random.nextDouble() > 0.4) { // 60% 확률로 값 존재
                    features.put("V" + v, random.nextDouble() * 10);
                }
            }

            // IEEE id_01-id_38 중 주요한 것들만
            for (int i = 1; i <= 20; i++) {
                if (random.nextDouble() > 0.5) { // 50% 확률로 값 존재
                    features.put("id_" + String.format("%02d", i), random.nextDouble() * 1000);
                }
            }

            // IEEE 디바이스 정보
            String[] deviceTypes = {"desktop", "mobile"};
            String[] deviceInfos = {"Windows", "iOS Device", "Android", "macOS"};
            features.put("DeviceType", deviceTypes[random.nextInt(deviceTypes.length)]);
            features.put("DeviceInfo", deviceInfos[random.nextInt(deviceInfos.length)]);

            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(features);

        } catch (Exception e) {
            log.warn("Failed to generate IEEE anonymized features: {}", e.getMessage());
            return "{}";
        }
    }

    private String generateMerchantFromProduct(String productCode) {
        if (productCode == null) return "Unknown_Merchant_" + new Random().nextInt(1000);
        
        switch (productCode) {
            case "W": return "WorldWide_Store_" + new Random().nextInt(1000);
            case "C": return "Card_Services_" + new Random().nextInt(100);
            case "H": return "Home_Shopping_" + new Random().nextInt(500);
            case "S": return "Service_Provider_" + new Random().nextInt(300);
            case "R": return "Retail_Chain_" + new Random().nextInt(200);
            default: return "Unknown_Merchant_" + new Random().nextInt(1000);
        }
    }

    private String getRandomProductCode() {
        String[] codes = {"W", "C", "H", "S", "R"};
        return codes[new Random().nextInt(codes.length)];
    }
    
    private void processTransaction(Transaction transaction) {
        if (transaction == null) {
            log.warn("Attempted to process null transaction");
            return;
        }
        
        try {
            // 트랜잭션 저장 및 사기 탐지 실행
            transactionService.processAndDetectFraud(transaction);
            
        } catch (Exception e) {
            log.error("Error processing transaction {}: {}", transaction.getId(), e.getMessage(), e);
        }
    }
    
    public void pauseStreaming() {
        StreamingConfig config = currentConfig.get();
        if (config != null) {
            config.setPaused(true);
            log.info("Transaction streaming paused");
            updateStreamingStatus();
            broadcastStreamingStatus();
        } else {
            log.warn("No active streaming to pause");
        }
    }
    
    public void resumeStreaming() {
        StreamingConfig config = currentConfig.get();
        if (config != null) {
            config.setPaused(false);
            log.info("Transaction streaming resumed");
            updateStreamingStatus();
            broadcastStreamingStatus();
        } else {
            log.warn("No active streaming to resume");
        }
    }
    
    public void stopStreaming() {
        isStreaming.set(false);
        
        try {
            scheduler.shutdown();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        log.info("Transaction streaming stopped");
        updateStreamingStatus();
        broadcastStreamingStatus();
    }
    
    public void updateSpeed(Double newSpeedMultiplier) {
        if (newSpeedMultiplier == null || newSpeedMultiplier <= 0) {
            throw new IllegalArgumentException("Speed multiplier must be positive");
        }
        
        StreamingConfig config = currentConfig.get();
        if (config != null) {
            config.setSpeedMultiplier(newSpeedMultiplier);
            log.info("Streaming speed updated to {}x", newSpeedMultiplier);
            updateStreamingStatus();
            broadcastStreamingStatus();
        } else {
            log.warn("No active streaming to update speed");
        }
    }
    
    public void jumpToTime(LocalDateTime targetTime) {
        if (targetTime == null) {
            throw new IllegalArgumentException("Target time cannot be null");
        }
        
        StreamingConfig config = currentConfig.get();
        if (config != null && 
            config.getMode() == StreamingConfig.StreamingMode.TIMEMACHINE) {
            
            currentVirtualTime.set(targetTime);
            log.info("Virtual time jumped to {}", targetTime);
            updateStreamingStatus();
            broadcastStreamingStatus();
        } else {
            log.warn("Time jump only available in TIME_MACHINE mode");
        }
    }
    
    private void updateStreamingStatus() {
        StreamingConfig config = currentConfig.get();
        LocalDateTime virtualTime = currentVirtualTime.get();
        
        streamingStatus.put("isStreaming", isStreaming.get());
        streamingStatus.put("isPaused", config != null ? config.getPaused() : false);
        streamingStatus.put("mode", config != null ? config.getMode().name() : "STOPPED");
        streamingStatus.put("speedMultiplier", config != null ? config.getSpeedMultiplier() : 0.0);
        streamingStatus.put("currentVirtualTime", virtualTime != null ? 
            virtualTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null);
        streamingStatus.put("updatedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
        if (config != null && config.getEndTime() != null && virtualTime != null) {
            LocalDateTime startTime = config.getStartTime() != null ? config.getStartTime() : DEFAULT_START_TIME;
            
            long totalMinutes = java.time.Duration.between(startTime, config.getEndTime()).toMinutes();
            long elapsedMinutes = java.time.Duration.between(startTime, virtualTime).toMinutes();
            
            double progress = totalMinutes > 0 ? (double) elapsedMinutes / totalMinutes : 0.0;
            streamingStatus.put("progress", Math.min(1.0, Math.max(0.0, progress)));
        }
    }
    
    private void broadcastStreamingStatus() {
        if (messagingTemplate != null) {
            try {
                messagingTemplate.convertAndSend("/topic/streaming-status", streamingStatus);
                log.debug("Streaming status broadcasted");
            } catch (Exception e) {
                log.warn("Failed to broadcast streaming status: {}", e.getMessage());
            }
        } else {
            log.debug("WebSocket messaging not available, skipping status broadcast");
        }
    }
    
    public Map<String, Object> getStreamingStatus() {
        return new HashMap<>(streamingStatus);
    }
    
    // 유틸리티 메서드들
    private String getRandomMerchantCategory() {
        String[] categories = {
            "GROCERY", "GAS_STATION", "RESTAURANT", "ONLINE_SHOPPING", 
            "ATM", "PHARMACY", "RETAIL", "ENTERTAINMENT", "TRAVEL", "UTILITY"
        };
        return categories[new Random().nextInt(categories.length)];
    }
    
    private String generateRandomIP() {
        Random random = new Random();
        return String.format("%d.%d.%d.%d", 
            random.nextInt(256), random.nextInt(256), 
            random.nextInt(256), random.nextInt(256));
    }
}
package com.credit.card.fraud.detection.transactions.service;

import com.credit.card.fraud.detection.transactions.dto.StreamingConfig;
import com.credit.card.fraud.detection.transactions.entity.Transaction;
import com.credit.card.fraud.detection.transactions.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
    
    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;
    private final SimpMessagingTemplate messagingTemplate;
    
    private final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(2);
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
        if (isStreaming.get()) {
            log.warn("Streaming is already running. Stop current streaming first.");
            return;
        }
        
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
    }
    
    private void processNextBatch() {
        if (!isStreaming.get() || currentConfig.get().getPaused()) {
            return;
        }
        
        try {
            StreamingConfig config = currentConfig.get();
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
        // 실제 운영 환경에서는 기존 데이터를 조회하거나 외부 API에서 데이터를 받음
        // 여기서는 시뮬레이션된 트랜잭션 생성
        
        List<Transaction> transactions = new ArrayList<>();
        Random random = new Random();
        
        // 시간당 평균 100~500개의 트랜잭션 생성
        int transactionCount = 100 + random.nextInt(400);
        
        for (int i = 0; i < transactionCount; i++) {
            LocalDateTime transactionTime = startTime.plusMinutes(random.nextInt(60));
            
            Transaction transaction = Transaction.builder()
                .userId("USER_" + (10000 + random.nextInt(90000)))
                .amount(BigDecimal.valueOf(10 + random.nextDouble() * 9990)
                       .setScale(2, BigDecimal.ROUND_HALF_UP))
                .merchant("MERCHANT_" + random.nextInt(1000))
                .merchantCategory(getRandomMerchantCategory())
                .transactionTime(transactionTime)
                .virtualTime(transactionTime) // 시연용이므로 동일하게 설정
                .latitude(BigDecimal.valueOf(37.5665 + (random.nextGaussian() * 0.1)))
                .longitude(BigDecimal.valueOf(126.9780 + (random.nextGaussian() * 0.1)))
                .deviceFingerprint("DEVICE_" + random.nextInt(10000))
                .ipAddress(generateRandomIP())
                .externalTransactionId(UUID.randomUUID().toString())
                .build();
            
            transactions.add(transaction);
        }
        
        return transactions;
    }
    
    private void processTransaction(Transaction transaction) {
        try {
            // 트랜잭션 저장 및 사기 탐지 실행
            transactionService.processAndDetectFraud(transaction);
            
        } catch (Exception e) {
            log.error("Error processing transaction {}: {}", transaction.getId(), e.getMessage());
        }
    }
    
    public void pauseStreaming() {
        if (currentConfig.get() != null) {
            currentConfig.get().setPaused(true);
            log.info("Transaction streaming paused");
            updateStreamingStatus();
            broadcastStreamingStatus();
        }
    }
    
    public void resumeStreaming() {
        if (currentConfig.get() != null) {
            currentConfig.get().setPaused(false);
            log.info("Transaction streaming resumed");
            updateStreamingStatus();
            broadcastStreamingStatus();
        }
    }
    
    public void stopStreaming() {
        isStreaming.set(false);
        scheduler.shutdownNow();
        
        log.info("Transaction streaming stopped");
        updateStreamingStatus();
        broadcastStreamingStatus();
    }
    
    public void updateSpeed(Double newSpeedMultiplier) {
        StreamingConfig config = currentConfig.get();
        if (config != null) {
            config.setSpeedMultiplier(newSpeedMultiplier);
            log.info("Streaming speed updated to {}x", newSpeedMultiplier);
            updateStreamingStatus();
            broadcastStreamingStatus();
        }
    }
    
    public void jumpToTime(LocalDateTime targetTime) {
        if (currentConfig.get() != null && 
            currentConfig.get().getMode() == StreamingConfig.StreamingMode.TIMEMACHINE) {
            
            currentVirtualTime.set(targetTime);
            log.info("Virtual time jumped to {}", targetTime);
            updateStreamingStatus();
            broadcastStreamingStatus();
        }
    }
    
    private void updateStreamingStatus() {
        StreamingConfig config = currentConfig.get();
        LocalDateTime virtualTime = currentVirtualTime.get();
        
        streamingStatus.put("isStreaming", isStreaming.get());
        streamingStatus.put("isPaused", config != null ? config.getPaused() : false);
        streamingStatus.put("mode", config != null ? config.getMode().name() : "STOPPED");
        streamingStatus.put("speedMultiplier", config != null ? config.getSpeedMultiplier() : 0.0);
        streamingStatus.put("currentVirtualTime", virtualTime != null ? virtualTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null);
        streamingStatus.put("updatedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
        if (config != null && config.getEndTime() != null && virtualTime != null) {
            double progress = (double) java.time.Duration.between(
                config.getStartTime() != null ? config.getStartTime() : DEFAULT_START_TIME, 
                virtualTime
            ).toMinutes() / java.time.Duration.between(
                config.getStartTime() != null ? config.getStartTime() : DEFAULT_START_TIME, 
                config.getEndTime()
            ).toMinutes();
            streamingStatus.put("progress", Math.min(1.0, Math.max(0.0, progress)));
        }
    }
    
    private void broadcastStreamingStatus() {
        messagingTemplate.convertAndSend("/topic/streaming-status", streamingStatus);
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
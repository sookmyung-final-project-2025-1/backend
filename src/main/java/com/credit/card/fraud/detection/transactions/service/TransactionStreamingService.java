package com.credit.card.fraud.detection.transactions.service;

import com.credit.card.fraud.detection.transactions.dto.StreamingConfig;
import com.credit.card.fraud.detection.transactions.entity.Transaction;
import com.credit.card.fraud.detection.transactions.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionStreamingService {

    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;

    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

    private final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(2);
    private final AtomicReference<StreamingConfig> currentConfig = new AtomicReference<>();
    private final AtomicReference<LocalDateTime> currentVirtualTime = new AtomicReference<>();
    private final AtomicBoolean isStreaming = new AtomicBoolean(false);
    private final Map<String, Object> streamingStatus = new ConcurrentHashMap<>();

    private static final LocalDateTime DEFAULT_START_TIME = LocalDateTime.of(2017, 1, 1, 0, 0);
    private static final LocalDateTime DATA_END_TIME = LocalDateTime.of(2019, 12, 31, 23, 59);

    public void startRealTimeStreaming() {
        // 데모 모드: 가장 오래된 거래 시간부터 시작
        LocalDateTime earliestTime = transactionRepository.findEarliestTransactionTime();
        LocalDateTime latestTime = transactionRepository.findLatestTransactionTime();

        startStreaming(StreamingConfig.builder()
                .mode(StreamingConfig.StreamingMode.REALTIME)
                .startTime(earliestTime != null ? earliestTime : DEFAULT_START_TIME)
                .endTime(latestTime != null ? latestTime : DATA_END_TIME)
                .speedMultiplier(1.0)
                .build());
    }

    public void startTimeMachineStreaming(LocalDateTime startTime, Double speedMultiplier) {
        startStreaming(StreamingConfig.builder()
                .mode(StreamingConfig.StreamingMode.TIMEMACHINE)
                .startTime(startTime != null ? startTime : DEFAULT_START_TIME)
                .endTime(DATA_END_TIME)
                .speedMultiplier(speedMultiplier != null ? speedMultiplier : 1.0)
                .build());
    }

    @Async
    public void startStreaming(StreamingConfig config) {
        if (isStreaming.compareAndSet(false, true)) {
            currentConfig.set(config);
            currentVirtualTime.set(config.getStartTime() != null ? config.getStartTime() : DEFAULT_START_TIME);

            log.info("Starting streaming - Mode: {}, Speed: {}x", config.getMode(), config.getSpeedMultiplier());

            scheduler.scheduleAtFixedRate(this::processNextBatch, 0, config.getAdjustedIntervalMs(), TimeUnit.MILLISECONDS);
        }
    }

    private void processNextBatch() {
        StreamingConfig config = currentConfig.get();
        if (!isStreaming.get() || config == null || config.getPaused()) return;

        LocalDateTime virtualTime = currentVirtualTime.get();
        if (config.getEndTime() != null && virtualTime.isAfter(config.getEndTime())) {
            stopStreaming();
            return;
        }

        LocalDateTime nextTime = config.getNextVirtualTime(virtualTime);
        List<Transaction> transactions = getActualTransactions(virtualTime, nextTime);

        currentVirtualTime.set(nextTime);
        broadcastRealtimeData(transactions);
    }

    private List<Transaction> getActualTransactions(LocalDateTime startTime, LocalDateTime endTime) {
        try {
            StreamingConfig config = currentConfig.get();

            if (config.getMode() == StreamingConfig.StreamingMode.REALTIME) {
                // 실시간 데모 모드: 현재 virtual time부터 다음 virtual time까지의 거래 조회
                LocalDateTime currentVirtual = currentVirtualTime.get();
                LocalDateTime nextVirtual = config.getNextVirtualTime(currentVirtual);

                return transactionRepository.findByVirtualTimeBetweenOrderByVirtualTime(currentVirtual, nextVirtual);
            } else {
                // 타임머신 모드: 지정된 시간 범위의 거래 데이터 조회
                return transactionRepository.findByVirtualTimeBetweenOrderByVirtualTime(startTime, endTime);
            }
        } catch (Exception e) {
            log.error("Failed to fetch actual transactions: {}", e.getMessage());
            return List.of(); // 빈 리스트 반환
        }
    }

    // 실시간 데이터 브로드캐스트 - 핵심 부분
    private void broadcastRealtimeData(List<Transaction> transactions) {
        if (messagingTemplate == null || transactions.isEmpty()) return;

        Map<String, Object> realtimeData = Map.of(
                "timestamp", LocalDateTime.now(),
                "transactionCount", transactions.size(),
                "virtualTime", currentVirtualTime.get(),
                "transactions", transactions.stream()
                        .limit(10) // 최근 10건만 전송
                        .map(this::toTransactionSummary)
                        .collect(Collectors.toList())
        );

        messagingTemplate.convertAndSend("/topic/realtime-transactions", realtimeData);
    }

    private Map<String, Object> toTransactionSummary(Transaction transaction) {
        return Map.of(
                "id", transaction.getId(),
                "amount", transaction.getAmount(),
                "merchant", transaction.getMerchant(),
                "time", transaction.getVirtualTime(),
                "isFraud", transaction.getIsFraud()
        );
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
}
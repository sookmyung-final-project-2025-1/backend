package com.credit.card.fraud.detection.modelclient.service;

import com.credit.card.fraud.detection.modelclient.dto.ConfidenceScoreResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
public class ConfidenceScoreService {

    // 신뢰도 점수 추적을 위한 메모리 저장소
    private final Map<String, List<ConfidenceDataPoint>> confidenceHistory = new ConcurrentHashMap<>();
    private final AtomicReference<BigDecimal> currentConfidenceScore = new AtomicReference<>(new BigDecimal("0.85"));

    // 모델 드리프트 시뮬레이션을 위한 변수들
    private final Random random = new Random();
    private volatile LocalDateTime lastUpdateTime = LocalDateTime.now();
    private volatile double driftRate = 0.001; // 시간당 0.1% 드리프트

    public ConfidenceScoreResponse getConfidenceScore(LocalDateTime startTime, LocalDateTime endTime, String period) {
        // 현재 신뢰도 점수 업데이트 (모델 드리프트 시뮬레이션)
        updateConfidenceScore();

        BigDecimal currentScore = currentConfidenceScore.get();

        // 시계열 데이터 생성
        List<ConfidenceScoreResponse.TimeSeriesData> timeSeries = generateConfidenceTimeSeries(
            startTime, endTime, period);

        return ConfidenceScoreResponse.builder()
            .currentConfidenceScore(currentScore)
            .calculatedAt(LocalDateTime.now())
            .timeSeries(timeSeries)
            .modelDriftStatus(calculateDriftStatus(currentScore))
            .alertThreshold(new BigDecimal("0.6"))
            .lastModelUpdate(getLastModelUpdateTime())
            .build();
    }

    public void recordConfidenceScore(BigDecimal score, LocalDateTime timestamp, Integer transactionCount) {
        if (score == null || timestamp == null) {
            throw new IllegalArgumentException("Score and timestamp cannot be null");
        }
        
        // 실제 운영에서는 실시간으로 confidence score를 계산하여 기록
        String hourKey = timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"));

        ConfidenceDataPoint dataPoint = new ConfidenceDataPoint(score, timestamp, transactionCount);

        confidenceHistory.computeIfAbsent(hourKey, k -> new ArrayList<>()).add(dataPoint);

        // 오래된 데이터 정리 (7일 이상)
        cleanupOldData();
    }

    private synchronized void updateConfidenceScore() {
        LocalDateTime now = LocalDateTime.now();
        long minutesSinceLastUpdate = java.time.Duration.between(lastUpdateTime, now).toMinutes();

        if (minutesSinceLastUpdate > 0) {
            BigDecimal currentScore = currentConfidenceScore.get();

            // 모델 드리프트 시뮬레이션: 시간이 지날수록 점진적으로 신뢰도 하락
            double driftAmount = driftRate * minutesSinceLastUpdate / 60.0; // 시간당 비율
            double noiseAmount = random.nextGaussian() * 0.02; // 노이즈 추가

            double newScore = currentScore.doubleValue() - driftAmount + noiseAmount;

            // 0.3 ~ 0.95 범위로 제한
            newScore = Math.max(0.3, Math.min(0.95, newScore));

            currentConfidenceScore.set(BigDecimal.valueOf(newScore).setScale(6, RoundingMode.HALF_UP));
            lastUpdateTime = now;

            log.debug("Confidence score updated: {} -> {}", currentScore, newScore);
        }
    }

    private List<ConfidenceScoreResponse.TimeSeriesData> generateConfidenceTimeSeries(
            LocalDateTime startTime, LocalDateTime endTime, String period) {

        List<ConfidenceScoreResponse.TimeSeriesData> timeSeries = new ArrayList<>();

        LocalDateTime current = startTime;
        BigDecimal baseScore = new BigDecimal("0.90"); // 시작 점수

        while (current.isBefore(endTime)) {
            LocalDateTime next = getNextPeriod(current, period);

            // 시뮬레이션된 confidence score 계산
            BigDecimal score = calculateSimulatedConfidenceScore(current, baseScore);
            Integer transactionCount = getSimulatedTransactionCount(current, period);

            ConfidenceScoreResponse.TimeSeriesData dataPoint =
                ConfidenceScoreResponse.TimeSeriesData.builder()
                    .timestamp(current)
                    .confidenceScore(score)
                    .transactionCount(transactionCount)
                    .period(period)
                    .isModelUpdatePoint(isModelUpdatePoint(current))
                    .build();

            timeSeries.add(dataPoint);
            current = next;

            // 시간이 지날수록 점진적으로 감소
            baseScore = baseScore.subtract(new BigDecimal("0.001"));
        }

        return timeSeries;
    }

    private BigDecimal calculateSimulatedConfidenceScore(LocalDateTime timestamp, BigDecimal baseScore) {
        // 시간대별 패턴과 랜덤 노이즈를 적용한 신뢰도 점수
        int hour = timestamp.getHour();

        // 새벽 시간대는 데이터가 적어 신뢰도가 낮음
        double hourlyAdjustment = 0.0;
        if (hour >= 0 && hour < 6) {
            hourlyAdjustment = -0.05; // 새벽시간 신뢰도 하락
        } else if (hour >= 9 && hour < 18) {
            hourlyAdjustment = 0.02; // 업무시간 신뢰도 상승
        }

        // 랜덤 노이즈 추가
        double noise = random.nextGaussian() * 0.03;

        double score = baseScore.doubleValue() + hourlyAdjustment + noise;
        return BigDecimal.valueOf(Math.max(0.1, Math.min(0.99, score)))
                        .setScale(6, RoundingMode.HALF_UP);
    }

    private Integer getSimulatedTransactionCount(LocalDateTime timestamp, String period) {
        int hour = timestamp.getHour();
        int baseCount;

        // 시간대별 트랜잭션 수 패턴
        if (hour >= 0 && hour < 6) baseCount = 50;
        else if (hour >= 6 && hour < 9) baseCount = 200;
        else if (hour >= 9 && hour < 18) baseCount = 350;
        else if (hour >= 18 && hour < 22) baseCount = 400;
        else baseCount = 150;

        // 기간별 배수 적용
        switch (period.toLowerCase()) {
            case "daily":
                return baseCount * 24;
            case "hourly":
            default:
                return baseCount + random.nextInt(100);
        }
    }

    private LocalDateTime getNextPeriod(LocalDateTime current, String period) {
        switch (period.toLowerCase()) {
            case "daily":
                return current.plusDays(1);
            case "hourly":
            default:
                return current.plusHours(1);
        }
    }

    private String calculateDriftStatus(BigDecimal currentScore) {
        // 드리프트 상태 계산
        if (currentScore.compareTo(new BigDecimal("0.8")) >= 0) {
            return "STABLE";
        } else if (currentScore.compareTo(new BigDecimal("0.6")) >= 0) {
            return "MINOR_DRIFT";
        } else {
            return "MAJOR_DRIFT";
        }
    }

    private void cleanupOldData() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        String cutoffKey = cutoff.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"));

        confidenceHistory.entrySet().removeIf(entry -> entry.getKey().compareTo(cutoffKey) < 0);
    }

    private boolean isModelUpdatePoint(LocalDateTime timestamp) {
        // 모델 업데이트 시점을 시뮬레이션 (예: 매일 자정)
        return timestamp.getHour() == 0 && timestamp.getMinute() == 0;
    }

    private LocalDateTime getLastModelUpdateTime() {
        // 마지막 모델 업데이트 시간 시뮬레이션
        return LocalDateTime.now().minusHours(random.nextInt(24) + 1);
    }

    public synchronized void triggerModelUpdate() {
        // 모델 업데이트 후 신뢰도 점수 향상 시뮬레이션
        BigDecimal currentScore = currentConfidenceScore.get();
        BigDecimal improvedScore = currentScore.add(new BigDecimal("0.05")) // 5% 향상
                                              .min(new BigDecimal("0.95")); // 최대 95%

        currentConfidenceScore.set(improvedScore);
        driftRate = Math.max(0.0005, driftRate * 0.8); // 드리프트 속도 감소

        log.info("Model updated: Confidence score improved from {} to {}", currentScore, improvedScore);
    }

    public BigDecimal getCurrentConfidenceScore() {
        updateConfidenceScore();
        return currentConfidenceScore.get();
    }

    // 내부 데이터 클래스
    private record ConfidenceDataPoint(BigDecimal score, LocalDateTime timestamp, Integer transactionCount) {}
}
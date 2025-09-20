package com.credit.card.fraud.detection.transactions.service;

import com.credit.card.fraud.detection.transactions.repository.FraudDetectionResultRepository;
import com.credit.card.fraud.detection.transactions.repository.TransactionRepository;
import com.credit.card.fraud.detection.transactions.repository.UserReportRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final TransactionRepository transactionRepository;
    private final FraudDetectionResultRepository fraudDetectionResultRepository;
    private final UserReportRepository userReportRepository;

    public Map<String, Object> getDashboardKPIs(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> kpis = new HashMap<>();

        // 기본 거래 통계
        Long totalTransactions = transactionRepository.countTransactionsInTimeWindow(startTime, endTime);
        Long fraudTransactions = transactionRepository.countFraudTransactionsInTimeWindow(startTime, endTime);
        Long uniqueUsers = transactionRepository.countUniqueUsersInTimeWindow(startTime, endTime);
        Double avgAmount = transactionRepository.averageTransactionAmountInTimeWindow(startTime, endTime);

        // 신규 유저 수 계산 (시간당)
        Long newUsersHourly = calculateNewUsersHourly(startTime, endTime);

        Double avgProcessingTime = null;
        Double medianProcessingTime = null;
        Double p95ProcessingTime = null;
        BigDecimal avgConfidenceScore = null;

        try {
            avgProcessingTime = fraudDetectionResultRepository.averageProcessingTime(startTime, endTime);
        } catch (Exception e) {
            avgProcessingTime = 0.0;
        }

        try {
            medianProcessingTime = fraudDetectionResultRepository.medianProcessingTime(startTime, endTime);
        } catch (Exception e) {
            medianProcessingTime = 0.0;
        }

        try {
            p95ProcessingTime = fraudDetectionResultRepository.p95ProcessingTime(startTime, endTime);
        } catch (Exception e) {
            p95ProcessingTime = 0.0;
        }

        try {
            avgConfidenceScore = fraudDetectionResultRepository.averageConfidenceScore(startTime, endTime);
        } catch (Exception e) {
            avgConfidenceScore = BigDecimal.ZERO;
        }

        // Throughput 계산 (시간당 처리 건수)
        long hours = java.time.Duration.between(startTime, endTime).toHours();
        Double throughput = hours > 0 ? totalTransactions.doubleValue() / hours : 0.0;

        // 사기율 계산
        Double fraudRate = totalTransactions > 0 ?
                (fraudTransactions.doubleValue() / totalTransactions.doubleValue()) * 100 : 0.0;

        // 평균 Latency (p50) 계산
        Double latencyP50 = medianProcessingTime;

        kpis.put("totalTransactions", totalTransactions);
        kpis.put("fraudTransactions", fraudTransactions);
        kpis.put("fraudRate", fraudRate);
        kpis.put("uniqueUsers", uniqueUsers);
        kpis.put("newUsersHourly", newUsersHourly);
        kpis.put("averageTransactionAmount", avgAmount != null ? avgAmount : 0.0);
        kpis.put("throughputPerHour", throughput);
        kpis.put("latencyP50Ms", latencyP50 != null ? latencyP50 : 0.0);
        kpis.put("latencyP95Ms", p95ProcessingTime != null ? p95ProcessingTime : 0.0);
        kpis.put("averageProcessingTimeMs", avgProcessingTime != null ? avgProcessingTime : 0.0);
        kpis.put("averageConfidenceScore", avgConfidenceScore != null ? avgConfidenceScore : BigDecimal.ZERO);

        // 추가 비즈니스 메트릭
        kpis.put("fraudAmount", calculateFraudAmount(startTime, endTime));
        kpis.put("blockedAmount", calculateBlockedAmount(startTime, endTime));
        kpis.put("falsePositiveRate", calculateFalsePositiveRate(startTime, endTime));

        return kpis;
    }

    private BigDecimal calculateFraudAmount(LocalDateTime startTime, LocalDateTime endTime) {
        // 실제 구현에서는 사기로 판정된 거래들의 총 금액 계산
        try {
            Double fraudAmount = transactionRepository.sumFraudTransactionAmounts(startTime, endTime);
            return fraudAmount != null ? BigDecimal.valueOf(fraudAmount) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal calculateBlockedAmount(LocalDateTime startTime, LocalDateTime endTime) {
        // 실시간 차단으로 인해 방지된 손실 추정
        BigDecimal fraudAmount = calculateFraudAmount(startTime, endTime);
        // 90%의 사기 거래가 차단되었다고 가정
        return fraudAmount.multiply(new BigDecimal("0.9"));
    }

    private Double calculateFalsePositiveRate(LocalDateTime startTime, LocalDateTime endTime) {
        try {
            Long totalFraudPredictions = fraudDetectionResultRepository.countFraudPredictions(startTime, endTime);
            Long confirmedFraudFromReports = userReportRepository.countConfirmedFraud(startTime, endTime);

            if (totalFraudPredictions > 0 && confirmedFraudFromReports != null) {
                Long falsePositives = totalFraudPredictions - confirmedFraudFromReports;
                return (falsePositives.doubleValue() / totalFraudPredictions.doubleValue()) * 100;
            }

            return 5.0; // 기본값 (실제 데이터 없을 때만)
        } catch (Exception e) {
            return 5.0;
        }
    }

    private Long calculateNewUsersHourly(LocalDateTime startTime, LocalDateTime endTime) {
        try {
            return transactionRepository.countNewUsersInPeriod(startTime, endTime);
        } catch (Exception e) {
            // Repository 메서드가 없는 경우에만 추정
            Long totalUsers = transactionRepository.countUniqueUsersInTimeWindow(startTime, endTime);
            long hours = Math.max(1, java.time.Duration.between(startTime, endTime).toHours());
            return Math.round(totalUsers * 0.08 / hours);
        }
    }

    public List<Map<String, Object>> getHourlyTransactionStats(LocalDateTime startTime, LocalDateTime endTime) {
        try {
            List<Object[]> hourlyStats = fraudDetectionResultRepository.getHourlyStats(startTime, endTime);

            return hourlyStats.stream()
                    .map(row -> {
                        Map<String, Object> stat = new HashMap<>();
                        stat.put("timestamp", row[0]);
                        stat.put("avgConfidenceScore", row[1]);
                        stat.put("totalCount", row[2]);
                        stat.put("avgProcessingTime", row[3]);
                        return stat;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public List<Map<String, Object>> getDailyTransactionStats(LocalDateTime startTime, LocalDateTime endTime) {
        try {
            List<Object[]> dailyStats = transactionRepository.getDailyTransactionStats(startTime, endTime);

            return dailyStats.stream()
                    .map(row -> {
                        Map<String, Object> stat = new HashMap<>();
                        stat.put("date", row[0]);
                        stat.put("totalCount", row[1]);
                        stat.put("fraudCount", row[2]);

                        Long total = ((Number) row[1]).longValue();
                        Long fraud = ((Number) row[2]).longValue();
                        Double fraudRate = total > 0 ? (fraud.doubleValue() / total.doubleValue()) * 100 : 0.0;
                        stat.put("fraudRate", fraudRate);

                        return stat;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public Map<String, Object> getRealTimeMetrics() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneHourAgo = now.minusHours(1);
        LocalDateTime fiveMinutesAgo = now.minusMinutes(5);

        // 최근 1시간 통계
        Map<String, Object> hourlyKPIs = getDashboardKPIs(oneHourAgo, now);

        // 최근 5분 통계 (즉시 반응성을 위한)
        Long recentTransactions = transactionRepository.countTransactionsInTimeWindow(fiveMinutesAgo, now);
        Long recentFraudTransactions = transactionRepository.countFraudTransactionsInTimeWindow(fiveMinutesAgo, now);

        Map<String, Object> realTimeMetrics = new HashMap<>();
        realTimeMetrics.put("hourly", hourlyKPIs);
        realTimeMetrics.put("recentTransactions", recentTransactions);
        realTimeMetrics.put("recentFraudTransactions", recentFraudTransactions);
        realTimeMetrics.put("timestamp", now);

        return realTimeMetrics;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTopRiskTransactions(Integer limit) {
        LocalDateTime startTime = LocalDateTime.now().minusHours(24);
        
        try {
            List<com.credit.card.fraud.detection.transactions.entity.FraudDetectionResult> highRiskTransactions =
                    fraudDetectionResultRepository.findHighRiskTransactions(new BigDecimal("0.7"), startTime);

            return highRiskTransactions.stream()
                    .limit(limit != null ? limit : 10)
                    .map(result -> {
                        Map<String, Object> transaction = new HashMap<>();
                        transaction.put("transactionId", result.getTransaction().getId());
                        transaction.put("fraudScore", result.getFinalScore());
                        transaction.put("predictionTime", result.getPredictionTime());
                        transaction.put("userId", result.getTransaction().getUserId());
                        transaction.put("amount", result.getTransaction().getAmount());
                        transaction.put("merchant", result.getTransaction().getMerchant());
                        return transaction;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public Map<String, Object> getSystemHealth() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneHourAgo = now.minusHours(1);

        Double avgProcessingTime = null;
        Double p95ProcessingTime = null;
        BigDecimal avgConfidence = null;

        try {
            avgProcessingTime = fraudDetectionResultRepository.averageProcessingTime(oneHourAgo, now);
        } catch (Exception e) {
            avgProcessingTime = 0.0;
        }

        try {
            p95ProcessingTime = fraudDetectionResultRepository.p95ProcessingTime(oneHourAgo, now);
        } catch (Exception e) {
            p95ProcessingTime = 0.0;
        }

        try {
            avgConfidence = fraudDetectionResultRepository.averageConfidenceScore(oneHourAgo, now);
        } catch (Exception e) {
            avgConfidence = BigDecimal.ZERO;
        }

        // 건강도 점수 계산 (0-100)
        int healthScore = 100;

        // 처리 시간이 너무 오래걸리면 감점
        if (avgProcessingTime != null && avgProcessingTime > 1000) {
            healthScore -= 20;
        }

        // P95가 너무 높으면 감점
        if (p95ProcessingTime != null && p95ProcessingTime > 5000) {
            healthScore -= 30;
        }

        // Confidence가 너무 낮으면 감점
        if (avgConfidence != null && avgConfidence.compareTo(new BigDecimal("0.5")) < 0) {
            healthScore -= 25;
        }

        String status;
        if (healthScore >= 80) status = "HEALTHY";
        else if (healthScore >= 60) status = "WARNING";
        else status = "CRITICAL";

        Map<String, Object> health = new HashMap<>();
        health.put("status", status);
        health.put("score", Math.max(0, healthScore));
        health.put("avgProcessingTime", avgProcessingTime);
        health.put("p95ProcessingTime", p95ProcessingTime);
        health.put("avgConfidenceScore", avgConfidence);
        health.put("checkedAt", now);

        return health;
    }
}
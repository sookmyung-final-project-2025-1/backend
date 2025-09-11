package com.credit.card.fraud.detection.transactions.service;

import com.credit.card.fraud.detection.transactions.repository.FraudDetectionResultRepository;
import com.credit.card.fraud.detection.transactions.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final TransactionRepository transactionRepository;
    private final FraudDetectionResultRepository fraudDetectionResultRepository;

    public Map<String, Object> getDashboardKPIs(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> kpis = new HashMap<>();
        
        // 기본 거래 통계
        Long totalTransactions = transactionRepository.countTransactionsInTimeWindow(startTime, endTime);
        Long fraudTransactions = transactionRepository.countFraudTransactionsInTimeWindow(startTime, endTime);
        Long uniqueUsers = transactionRepository.countUniqueUsersInTimeWindow(startTime, endTime);
        Double avgAmount = transactionRepository.averageTransactionAmountInTimeWindow(startTime, endTime);
        
        // 처리 성능 통계
        Double avgProcessingTime = fraudDetectionResultRepository.averageProcessingTime(startTime, endTime);
        Double medianProcessingTime = fraudDetectionResultRepository.medianProcessingTime(startTime, endTime);
        Double p95ProcessingTime = fraudDetectionResultRepository.p95ProcessingTime(startTime, endTime);
        
        // Confidence Score
        BigDecimal avgConfidenceScore = fraudDetectionResultRepository.averageConfidenceScore(startTime, endTime);
        
        // Throughput 계산 (시간당 처리 건수)
        long hours = java.time.Duration.between(startTime, endTime).toHours();
        Double throughput = hours > 0 ? totalTransactions.doubleValue() / hours : 0.0;
        
        // 사기율 계산
        Double fraudRate = totalTransactions > 0 ? 
            (fraudTransactions.doubleValue() / totalTransactions.doubleValue()) * 100 : 0.0;

        kpis.put("totalTransactions", totalTransactions);
        kpis.put("fraudTransactions", fraudTransactions);
        kpis.put("fraudRate", fraudRate);
        kpis.put("uniqueUsers", uniqueUsers);
        kpis.put("averageTransactionAmount", avgAmount != null ? avgAmount : 0.0);
        kpis.put("throughputPerHour", throughput);
        kpis.put("averageProcessingTimeMs", avgProcessingTime != null ? avgProcessingTime : 0.0);
        kpis.put("medianProcessingTimeMs", medianProcessingTime != null ? medianProcessingTime : 0.0);
        kpis.put("p95ProcessingTimeMs", p95ProcessingTime != null ? p95ProcessingTime : 0.0);
        kpis.put("averageConfidenceScore", avgConfidenceScore != null ? avgConfidenceScore : BigDecimal.ZERO);
        
        return kpis;
    }

    public List<Map<String, Object>> getHourlyTransactionStats(LocalDateTime startTime, LocalDateTime endTime) {
        List<Object[]> hourlyStats = fraudDetectionResultRepository.getHourlyStats(startTime, endTime);
        
        return hourlyStats.stream()
            .map(row -> {
                Map<String, Object> stat = new HashMap<>();
                stat.put("timestamp", row[0]); // hour
                stat.put("avgConfidenceScore", row[1]); // avgConfidence
                stat.put("totalCount", row[2]); // totalCount
                stat.put("avgProcessingTime", row[3]); // avgProcessingTime
                return stat;
            })
            .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getDailyTransactionStats(LocalDateTime startTime, LocalDateTime endTime) {
        List<Object[]> dailyStats = transactionRepository.getDailyTransactionStats(startTime, endTime);
        
        return dailyStats.stream()
            .map(row -> {
                Map<String, Object> stat = new HashMap<>();
                stat.put("date", row[0]); // date
                stat.put("totalCount", row[1]); // count
                stat.put("fraudCount", row[2]); // fraudCount
                
                Long total = (Long) row[1];
                Long fraud = (Long) row[2];
                Double fraudRate = total > 0 ? (fraud.doubleValue() / total.doubleValue()) * 100 : 0.0;
                stat.put("fraudRate", fraudRate);
                
                return stat;
            })
            .collect(Collectors.toList());
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

    public List<Map<String, Object>> getTopRiskTransactions(Integer limit) {
        LocalDateTime startTime = LocalDateTime.now().minusHours(24);
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
    }

    public Map<String, Object> getSystemHealth() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneHourAgo = now.minusHours(1);
        
        // 시스템 건강도 지표들
        Double avgProcessingTime = fraudDetectionResultRepository.averageProcessingTime(oneHourAgo, now);
        Double p95ProcessingTime = fraudDetectionResultRepository.p95ProcessingTime(oneHourAgo, now);
        BigDecimal avgConfidence = fraudDetectionResultRepository.averageConfidenceScore(oneHourAgo, now);
        
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
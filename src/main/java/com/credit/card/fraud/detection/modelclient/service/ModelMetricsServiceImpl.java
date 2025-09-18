package com.credit.card.fraud.detection.modelclient.service;

import com.credit.card.fraud.detection.modelclient.dto.ConfidenceScoreResponse;
import com.credit.card.fraud.detection.transactions.repository.FraudDetectionResultRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ModelMetricsServiceImpl implements ModelMetricsService {

    private final FraudDetectionResultRepository fraudDetectionResultRepository;
    private final DataTypeConverter dataTypeConverter;

    @Override
    public ConfidenceScoreResponse getConfidenceScore(LocalDateTime startTime, LocalDateTime endTime, String period) {
        BigDecimal currentScore = getCurrentConfidenceScore(startTime, endTime);
        List<ConfidenceScoreResponse.TimeSeriesPoint> timeSeries = getTimeSeriesData(startTime, endTime, period);
        
        return ConfidenceScoreResponse.of(currentScore, timeSeries);
    }

    private BigDecimal getCurrentConfidenceScore(LocalDateTime startTime, LocalDateTime endTime) {
        try {
            BigDecimal score = fraudDetectionResultRepository.averageConfidenceScore(startTime, endTime);
            return score != null ? score : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private List<ConfidenceScoreResponse.TimeSeriesPoint> getTimeSeriesData(LocalDateTime startTime, LocalDateTime endTime, String period) {
        try {
            List<Object[]> hourlyStats = fraudDetectionResultRepository.getHourlyStats(startTime, endTime);
            return hourlyStats.stream()
                .map(row -> dataTypeConverter.convertToTimeSeriesPoint(row, period))
                .toList();
        } catch (Exception e) {
            return List.of();
        }
    }
}
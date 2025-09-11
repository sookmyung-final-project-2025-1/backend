package com.credit.card.fraud.detection.modelclient.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfidenceScoreResponse {
    
    private BigDecimal currentConfidenceScore;
    private LocalDateTime calculatedAt;
    private List<TimeSeriesPoint> timeSeries;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TimeSeriesPoint {
        private LocalDateTime timestamp;
        private BigDecimal confidenceScore;
        private Long transactionCount;
        private String period; // "hourly", "daily"
    }
    
    public static ConfidenceScoreResponse of(BigDecimal currentScore, List<TimeSeriesPoint> timeSeries) {
        return ConfidenceScoreResponse.builder()
            .currentConfidenceScore(currentScore)
            .calculatedAt(LocalDateTime.now())
            .timeSeries(timeSeries)
            .build();
    }
}
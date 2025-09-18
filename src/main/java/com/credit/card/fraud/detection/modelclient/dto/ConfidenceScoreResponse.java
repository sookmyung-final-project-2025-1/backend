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
    private List<TimeSeriesData> timeSeries;

    // 모델 드리프트 관련 정보
    private String modelDriftStatus;  // STABLE, MINOR_DRIFT, MAJOR_DRIFT
    private BigDecimal alertThreshold;
    private LocalDateTime lastModelUpdate;
    private BigDecimal scoreBeforeUpdate;
    private BigDecimal scoreAfterUpdate;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TimeSeriesData {
        private LocalDateTime timestamp;
        private BigDecimal confidenceScore;
        private Integer transactionCount;
        private String period;
        private Boolean isModelUpdatePoint; // 모델 업데이트 시점 마커
    }

    // 기존 호환성을 위한 별칭
    @Deprecated
    public static class TimeSeriesPoint extends TimeSeriesData {
        public TimeSeriesPoint() {
            super();
        }

        public TimeSeriesPoint(LocalDateTime timestamp, BigDecimal confidenceScore, Integer transactionCount, String period, Boolean isModelUpdatePoint) {
            super(timestamp, confidenceScore, transactionCount, period, isModelUpdatePoint);
        }

        public Long getTransactionCountAsLong() {
            return super.getTransactionCount() != null ? super.getTransactionCount().longValue() : null;
        }

        public void setTransactionCountFromLong(Long transactionCount) {
            super.setTransactionCount(transactionCount != null ? transactionCount.intValue() : null);
        }
    }

    public static ConfidenceScoreResponse of(BigDecimal currentScore, List<TimeSeriesData> timeSeries) {
        return ConfidenceScoreResponse.builder()
            .currentConfidenceScore(currentScore)
            .calculatedAt(LocalDateTime.now())
            .timeSeries(timeSeries)
            .build();
    }
}
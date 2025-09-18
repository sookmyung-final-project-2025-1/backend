package com.credit.card.fraud.detection.modelclient.service;

import com.credit.card.fraud.detection.modelclient.dto.ConfidenceScoreResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class DataTypeConverter {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ConfidenceScoreResponse.TimeSeriesPoint convertToTimeSeriesPoint(Object[] row, String period) {
        LocalDateTime timestamp = parseTimestamp(row[0]);
        BigDecimal confidenceScore = parseConfidenceScore(row[1]);
        Long transactionCount = parseTransactionCount(row[2]);
        
        return ConfidenceScoreResponse.TimeSeriesPoint.builder()
            .timestamp(timestamp)
            .confidenceScore(confidenceScore)
            .transactionCount(transactionCount)
            .period(period)
            .build();
    }

    private LocalDateTime parseTimestamp(Object obj) {
        if (obj instanceof LocalDateTime) {
            return (LocalDateTime) obj;
        } else if (obj instanceof String) {
            try {
                return LocalDateTime.parse((String) obj, FORMATTER);
            } catch (Exception e) {
                return LocalDateTime.now();
            }
        }
        return LocalDateTime.now();
    }

    private BigDecimal parseConfidenceScore(Object obj) {
        if (obj instanceof BigDecimal) {
            return (BigDecimal) obj;
        } else if (obj instanceof Number) {
            return BigDecimal.valueOf(((Number) obj).doubleValue());
        }
        return BigDecimal.ZERO;
    }

    private Long parseTransactionCount(Object obj) {
        if (obj instanceof Long) {
            return (Long) obj;
        } else if (obj instanceof Number) {
            return ((Number) obj).longValue();
        }
        return 0L;
    }
}
package com.credit.card.fraud.detection.modelclient.service;

import com.credit.card.fraud.detection.modelclient.dto.ConfidenceScoreResponse;
import java.time.LocalDateTime;

public interface ModelMetricsService {
    ConfidenceScoreResponse getConfidenceScore(LocalDateTime startTime, LocalDateTime endTime, String period);
}
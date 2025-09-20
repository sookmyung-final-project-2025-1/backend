package com.credit.card.fraud.detection.transactions.service;

import com.credit.card.fraud.detection.modelclient.dto.ModelPredictionResponse;
import com.credit.card.fraud.detection.transactions.entity.FraudDetectionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModelOutputSerializer {

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public void serializeModelOutputs(ModelPredictionResponse response, FraudDetectionResult detectionResult) {
        try {
            if (response.getFeatureImportance() != null) {
                detectionResult.setFeatureImportance(
                        objectMapper.writeValueAsString(response.getFeatureImportance()));
            }
            if (response.getAttentionScores() != null) {
                detectionResult.setAttentionScores(
                        objectMapper.writeValueAsString(response.getAttentionScores()));
            }
        } catch (Exception e) {
            log.warn("Failed to serialize model outputs for transaction {}: {}",
                    detectionResult.getTransaction().getId(), e.getMessage());
        }
    }
}
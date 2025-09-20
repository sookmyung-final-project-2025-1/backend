package com.credit.card.fraud.detection.transactions.service;

import com.credit.card.fraud.detection.modelclient.dto.ModelPredictionResponse;
import com.credit.card.fraud.detection.transactions.entity.FraudDetectionResult;
import com.credit.card.fraud.detection.transactions.entity.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModelResponseProcessor {

    private final ModelOutputSerializer serializer;

    public FraudDetectionResult processResponse(Transaction transaction, ModelPredictionResponse response) {
        FraudDetectionResult detectionResult = FraudDetectionResult.builder()
                .transaction(transaction)
                .lgbmScore(response.getLgbmScore())
                .xgboostScore(response.getXgboostScore())
                .catboostScore(response.getCatboostScore())
                .finalScore(response.getFinalScore())
                .finalPrediction(response.getFinalPrediction())
                .confidenceScore(response.getConfidenceScore())
                .lgbmWeight(response.getLgbmWeight())
                .xgboostWeight(response.getXgboostWeight())
                .catboostWeight(response.getCatboostWeight())
                .threshold(response.getThreshold())
                .predictionTime(response.getPredictionTime())
                .processingTimeMs(response.getProcessingTimeMs())
                .modelVersion(response.getModelVersion())
                .build();

        // 위험도 계산
        detectionResult.calculateRiskLevel();

        // 복잡한 JSON 직렬화는 별도 서비스에 위임
        serializer.serializeModelOutputs(response, detectionResult);

        return detectionResult;
    }
}
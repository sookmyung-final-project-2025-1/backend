package com.credit.card.fraud.detection.transactions.service;

import com.credit.card.fraud.detection.modelclient.dto.ModelPredictionRequest;
import com.credit.card.fraud.detection.modelclient.dto.ModelPredictionResponse;
import com.credit.card.fraud.detection.modelclient.service.EnsembleModelService;
import com.credit.card.fraud.detection.transactions.entity.FraudDetectionResult;
import com.credit.card.fraud.detection.transactions.entity.Transaction;
import com.credit.card.fraud.detection.transactions.exceptions.FraudDetectionException;
import com.credit.card.fraud.detection.transactions.repository.FraudDetectionResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사기 탐지
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FraudDetectionEngine {

    private final EnsembleModelService ensembleModelService;
    private final FraudDetectionResultRepository fraudDetectionResultRepository;
    private final ModelRequestBuilder modelRequestBuilder;
    private final ModelResponseProcessor modelResponseProcessor;

    @Transactional
    public FraudDetectionResult detectFraud(Transaction transaction) {
        try {
            // 1. 모델 예측 요청 생성
            ModelPredictionRequest request = modelRequestBuilder.buildRequest(transaction);

            // 2. 앙상블 모델로 예측 실행
            ModelPredictionResponse response = ensembleModelService.predict(request);

            if (!response.getSuccess()) {
                throw new FraudDetectionException("Model prediction failed: " + response.getErrorMessage());
            }

            // 3. 예측 결과를 엔티티로 변환
            FraudDetectionResult detectionResult = modelResponseProcessor.processResponse(transaction, response);

            // 4. 결과 저장
            return fraudDetectionResultRepository.save(detectionResult);

        } catch (Exception e) {
            log.error("Error detecting fraud for transaction {}: {}", transaction.getId(), e.getMessage(), e);
            throw new FraudDetectionException("Failed to detect fraud: " + e.getMessage(), e);
        }
    }
}
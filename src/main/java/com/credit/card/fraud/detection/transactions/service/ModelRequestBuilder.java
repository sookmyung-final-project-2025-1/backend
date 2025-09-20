package com.credit.card.fraud.detection.transactions.service;

import com.credit.card.fraud.detection.modelclient.dto.ModelPredictionRequest;
import com.credit.card.fraud.detection.modelclient.service.EnsembleModelService;
import com.credit.card.fraud.detection.transactions.entity.Transaction;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 모델 요청 생성
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ModelRequestBuilder {

    private final EnsembleModelService ensembleModelService;
    private final ObjectMapper objectMapper;
    private final IEEEFeatureExtractor featureExtractor;

    public ModelPredictionRequest buildRequest(Transaction transaction) {
        try {
            Map<String, Object> ieeeFeatures = featureExtractor.extractFeatures(transaction);

            return ModelPredictionRequest.builder()
                    .transactionId(transaction.getId())
                    .amount(transaction.getAmount())

                    // IEEE 기본 필드들
                    .productCode(getStringValue(ieeeFeatures, "ProductCD", "W"))
                    .card1(getStringValue(ieeeFeatures, "card1", "13553"))
                    .card2(getStringValue(ieeeFeatures, "card2", "150.0"))
                    .card3(getStringValue(ieeeFeatures, "card3", "150.0"))
                    .addr1(getBigDecimalValue(ieeeFeatures, "addr1"))
                    .addr2(getBigDecimalValue(ieeeFeatures, "addr2"))
                    .dist1(getBigDecimalValue(ieeeFeatures, "dist1"))
                    .purchaserEmailDomain(getStringValue(ieeeFeatures, "P_emaildomain", "gmail.com"))

                    // IEEE 피처 맵들
                    .countingFeatures(extractFeatureMap(ieeeFeatures, "C", 1, 14))
                    .timeDeltas(extractFeatureMap(ieeeFeatures, "D", 1, 15))
                    .matchFeatures(extractMatchFeatures(ieeeFeatures))
                    .vestaFeatures(extractFeatureMap(ieeeFeatures, "V", 1, 339))
                    .identityFeatures(extractFeatureMap(ieeeFeatures, "id_", 1, 38))

                    // 모델 설정
                    .modelWeights(ensembleModelService.getCurrentWeights())
                    .threshold(ensembleModelService.getCurrentThreshold())
                    .build();

        } catch (Exception e) {
            log.error("Error building prediction request for transaction {}: {}",
                    transaction.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to build prediction request: " + e.getMessage(), e);
        }
    }

    private String getStringValue(Map<String, Object> features, String key, String defaultValue) {
        Object value = features.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private BigDecimal getBigDecimalValue(Map<String, Object> features, String key) {
        Object value = features.get(key);
        if (value == null) return null;

        try {
            if (value instanceof Number) {
                return BigDecimal.valueOf(((Number) value).doubleValue());
            }
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, BigDecimal> extractFeatureMap(Map<String, Object> features, String prefix, int start, int end) {
        Map<String, BigDecimal> result = new HashMap<>();

        for (int i = start; i <= end; i++) {
            String key = prefix.equals("id_") ?
                    String.format("%s%02d", prefix, i) :
                    prefix + i;

            BigDecimal value = getBigDecimalValue(features, key);
            if (value != null) {
                result.put(key, value);
            }
        }

        return result;
    }

    private Map<String, String> extractMatchFeatures(Map<String, Object> features) {
        Map<String, String> result = new HashMap<>();

        for (int i = 1; i <= 9; i++) {
            String key = "M" + i;
            String value = getStringValue(features, key, null);
            if (value != null && (value.equals("T") || value.equals("F"))) {
                result.put(key, value);
            }
        }

        return result;
    }
}
package com.credit.card.fraud.detection.transactions.service;

import com.credit.card.fraud.detection.transactions.entity.Transaction;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class IEEEFeatureExtractor {

    private final ObjectMapper objectMapper;

    @SuppressWarnings("unchecked")
    public Map<String, Object> extractFeatures(Transaction transaction) {
        if (transaction.getAnonymizedFeatures() == null ||
                transaction.getAnonymizedFeatures().trim().isEmpty()) {
            return new HashMap<>();
        }

        try {
            return objectMapper.readValue(transaction.getAnonymizedFeatures(), Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse IEEE features from transaction {}: {}",
                    transaction.getId(), e.getMessage());
            return new HashMap<>();
        }
    }
}

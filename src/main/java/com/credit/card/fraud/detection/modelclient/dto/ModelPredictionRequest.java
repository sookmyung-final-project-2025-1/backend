package com.credit.card.fraud.detection.modelclient.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModelPredictionRequest {
    
    private Long transactionId;
    private String userId;
    private BigDecimal amount;
    private String merchant;
    private String merchantCategory;
    private LocalDateTime transactionTime;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String deviceFingerprint;
    private String ipAddress;
    
    // 익명화된 피처들 (C1, C2, ..., C28 등)
    private Map<String, Object> anonymizedFeatures;
    
    // 모델 설정
    private Map<String, BigDecimal> modelWeights;
    private BigDecimal threshold;
    private String modelVersion;
}
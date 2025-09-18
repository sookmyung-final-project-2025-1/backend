package com.credit.card.fraud.detection.modelclient.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomWeightPredictionRequest {

    @Valid
    @NotNull(message = "Transaction request is required")
    private ModelPredictionRequest transactionRequest;

    @NotNull(message = "LGBM weight is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "LGBM weight must be greater than 0")
    @DecimalMax(value = "1.0", inclusive = true, message = "LGBM weight must be less than or equal to 1")
    private BigDecimal lgbmWeight;

    @NotNull(message = "XGBoost weight is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "XGBoost weight must be greater than 0")
    @DecimalMax(value = "1.0", inclusive = true, message = "XGBoost weight must be less than or equal to 1")
    private BigDecimal xgboostWeight;

    @NotNull(message = "CatBoost weight is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "CatBoost weight must be greater than 0")
    @DecimalMax(value = "1.0", inclusive = true, message = "CatBoost weight must be less than or equal to 1")
    private BigDecimal catboostWeight;

    /**
     * 자동 정규화 여부 (기본값: true)
     */
    @Builder.Default
    private Boolean autoNormalize = true;

    /**
     * 가중치 합계 계산
     */
    public BigDecimal getWeightSum() {
        return lgbmWeight.add(xgboostWeight).add(catboostWeight);
    }

    /**
     * 가중치 합이 1인지 검증 (허용 오차 0.001)
     */
    public boolean isValidWeightSum() {
        BigDecimal sum = getWeightSum();
        BigDecimal tolerance = new BigDecimal("0.001");
        return sum.subtract(BigDecimal.ONE).abs().compareTo(tolerance) <= 0;
    }
}
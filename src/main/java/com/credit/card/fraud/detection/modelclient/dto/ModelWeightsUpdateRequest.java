package com.credit.card.fraud.detection.modelclient.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModelWeightsUpdateRequest {
    
    @NotNull
    @DecimalMin(value = "0.0", message = "LGBM weight must be non-negative")
    @DecimalMax(value = "1.0", message = "LGBM weight must not exceed 1.0")
    private BigDecimal lgbmWeight;
    
    @NotNull
    @DecimalMin(value = "0.0", message = "XGBoost weight must be non-negative")
    @DecimalMax(value = "1.0", message = "XGBoost weight must not exceed 1.0")
    private BigDecimal xgboostWeight;
    
    @NotNull
    @DecimalMin(value = "0.0", message = "CatBoost weight must be non-negative")
    @DecimalMax(value = "1.0", message = "CatBoost weight must not exceed 1.0")
    private BigDecimal catboostWeight;
    
    @Builder.Default
    private Boolean autoNormalize = true;
    
    public void normalizeWeights() {
        BigDecimal sum = lgbmWeight.add(xgboostWeight).add(catboostWeight);
        if (sum.compareTo(BigDecimal.ZERO) > 0) {
            lgbmWeight = lgbmWeight.divide(sum, 6, BigDecimal.ROUND_HALF_UP);
            xgboostWeight = xgboostWeight.divide(sum, 6, BigDecimal.ROUND_HALF_UP);
            catboostWeight = catboostWeight.divide(sum, 6, BigDecimal.ROUND_HALF_UP);
        }
    }
    
    public boolean isValidWeightSum() {
        BigDecimal sum = lgbmWeight.add(xgboostWeight).add(catboostWeight);
        return sum.compareTo(BigDecimal.ONE) == 0;
    }
}
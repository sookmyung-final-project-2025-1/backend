package com.credit.card.fraud.detection.modelclient.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModelWeightsUpdateRequest {
    
    @NotNull(message = "LGBM weight cannot be null")
    @DecimalMin(value = "0.0", message = "LGBM weight must be non-negative")
    @DecimalMax(value = "1.0", message = "LGBM weight must not exceed 1.0")
    private BigDecimal lgbmWeight;
    
    @NotNull(message = "XGBoost weight cannot be null")
    @DecimalMin(value = "0.0", message = "XGBoost weight must be non-negative")
    @DecimalMax(value = "1.0", message = "XGBoost weight must not exceed 1.0")
    private BigDecimal xgboostWeight;
    
    @NotNull(message = "CatBoost weight cannot be null")
    @DecimalMin(value = "0.0", message = "CatBoost weight must be non-negative")
    @DecimalMax(value = "1.0", message = "CatBoost weight must not exceed 1.0")
    private BigDecimal catboostWeight;
    
    @Builder.Default
    private Boolean autoNormalize = true;
    
    // 가중치 합 허용 오차 (부동 소수점 연산 오차 고려)
    private static final BigDecimal TOLERANCE = new BigDecimal("0.0001");
    
    /**
     * 가중치 정규화 수행
     * @throws IllegalStateException 모든 가중치가 0인 경우
     */
    public void normalizeWeights() {
        validateWeights();
        
        BigDecimal sum = getWeightSum();
        if (sum.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalStateException("Cannot normalize weights: all weights are zero");
        }
        
        lgbmWeight = lgbmWeight.divide(sum, 6, RoundingMode.HALF_UP);
        xgboostWeight = xgboostWeight.divide(sum, 6, RoundingMode.HALF_UP);
        catboostWeight = catboostWeight.divide(sum, 6, RoundingMode.HALF_UP);
        
        // 정규화 후 합이 정확히 1이 되도록 조정
        adjustToExactSum();
    }
    
    /**
     * 가중치 합이 유효한지 확인 (1.0에 가까운지)
     * @return 가중치 합이 유효하면 true
     */
    public boolean isValidWeightSum() {
        validateWeights();
        
        BigDecimal sum = getWeightSum();
        BigDecimal difference = sum.subtract(BigDecimal.ONE).abs();
        return difference.compareTo(TOLERANCE) <= 0;
    }
    
    /**
     * 정확한 가중치 합이 1이 되도록 조정
     * 가장 큰 가중치에 조정값을 적용
     */
    private void adjustToExactSum() {
        BigDecimal currentSum = getWeightSum();
        BigDecimal adjustment = BigDecimal.ONE.subtract(currentSum);
        
        if (adjustment.abs().compareTo(TOLERANCE) > 0) {
            // 가장 큰 가중치를 찾아서 조정
            if (lgbmWeight.compareTo(xgboostWeight) >= 0 && lgbmWeight.compareTo(catboostWeight) >= 0) {
                lgbmWeight = lgbmWeight.add(adjustment);
            } else if (xgboostWeight.compareTo(catboostWeight) >= 0) {
                xgboostWeight = xgboostWeight.add(adjustment);
            } else {
                catboostWeight = catboostWeight.add(adjustment);
            }
        }
    }
    
    /**
     * 가중치 합 계산
     * @return 모든 가중치의 합
     */
    public BigDecimal getWeightSum() {
        validateWeights();
        return lgbmWeight.add(xgboostWeight).add(catboostWeight);
    }
    
    /**
     * 가중치 유효성 검증
     * @throws IllegalStateException 가중치가 null이거나 범위를 벗어난 경우
     */
    private void validateWeights() {
        if (lgbmWeight == null || xgboostWeight == null || catboostWeight == null) {
            throw new IllegalStateException("All weights must be non-null");
        }
        
        if (lgbmWeight.compareTo(BigDecimal.ZERO) < 0 || lgbmWeight.compareTo(BigDecimal.ONE) > 0 ||
            xgboostWeight.compareTo(BigDecimal.ZERO) < 0 || xgboostWeight.compareTo(BigDecimal.ONE) > 0 ||
            catboostWeight.compareTo(BigDecimal.ZERO) < 0 || catboostWeight.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalStateException("All weights must be between 0.0 and 1.0");
        }
    }
    
    /**
     * 균등 가중치로 초기화
     * @return 균등하게 분배된 가중치를 가진 새 인스턴스
     */
    public static ModelWeightsUpdateRequest createEqualWeights() {
        BigDecimal equalWeight = new BigDecimal("0.333333");
        return ModelWeightsUpdateRequest.builder()
            .lgbmWeight(equalWeight)
            .xgboostWeight(equalWeight)
            .catboostWeight(new BigDecimal("0.333334")) // 합이 정확히 1이 되도록
            .autoNormalize(false)
            .build();
    }
    
    /**
     * 가중치 정보를 문자열로 반환
     * @return 가중치 정보가 포함된 문자열
     */
    public String getWeightsInfo() {
        return String.format("LGBM: %s, XGBoost: %s, CatBoost: %s (Sum: %s)", 
            lgbmWeight, xgboostWeight, catboostWeight, getWeightSum());
    }
    
    /**
     * 자동 정규화가 활성화된 경우 가중치를 정규화하고 유효성을 검증
     * @return 유효한 가중치를 가진 새 인스턴스 또는 현재 인스턴스
     */
    public ModelWeightsUpdateRequest processWeights() {
        if (autoNormalize != null && autoNormalize) {
            ModelWeightsUpdateRequest normalized = ModelWeightsUpdateRequest.builder()
                .lgbmWeight(this.lgbmWeight)
                .xgboostWeight(this.xgboostWeight)
                .catboostWeight(this.catboostWeight)
                .autoNormalize(this.autoNormalize)
                .build();
            normalized.normalizeWeights();
            return normalized;
        } else {
            if (!isValidWeightSum()) {
                throw new IllegalArgumentException(
                    "Invalid weight sum: " + getWeightSum() + ". Sum must equal 1.0 when autoNormalize is disabled.");
            }
            return this;
        }
    }
}
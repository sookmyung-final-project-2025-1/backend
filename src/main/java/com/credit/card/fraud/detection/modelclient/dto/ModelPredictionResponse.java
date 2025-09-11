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
public class ModelPredictionResponse {
    
    private Long transactionId;
    
    // 개별 모델 점수
    private BigDecimal lgbmScore;
    private BigDecimal xgboostScore;
    private BigDecimal catboostScore;
    
    // 최종 앙상블 결과
    private BigDecimal finalScore;
    private Boolean finalPrediction;
    
    // 메타데이터
    private BigDecimal confidenceScore;
    private LocalDateTime predictionTime;
    private Long processingTimeMs;
    private String modelVersion;
    
    // 사용된 가중치
    private BigDecimal lgbmWeight;
    private BigDecimal xgboostWeight;
    private BigDecimal catboostWeight;
    private BigDecimal threshold;
    
    // 해석 가능성 정보
    private Map<String, BigDecimal> featureImportance;
    private Map<String, BigDecimal> attentionScores;
    
    // 에러 정보
    private String errorMessage;
    private Boolean success;
    
    public static ModelPredictionResponse success(Long transactionId, 
                                                BigDecimal lgbmScore, 
                                                BigDecimal xgboostScore, 
                                                BigDecimal catboostScore,
                                                BigDecimal lgbmWeight,
                                                BigDecimal xgboostWeight,
                                                BigDecimal catboostWeight,
                                                BigDecimal threshold,
                                                Long processingTimeMs) {
        
        BigDecimal finalScore = lgbmScore.multiply(lgbmWeight)
            .add(xgboostScore.multiply(xgboostWeight))
            .add(catboostScore.multiply(catboostWeight));
        
        Boolean finalPrediction = finalScore.compareTo(threshold) > 0;
        
        return ModelPredictionResponse.builder()
            .transactionId(transactionId)
            .lgbmScore(lgbmScore)
            .xgboostScore(xgboostScore)
            .catboostScore(catboostScore)
            .finalScore(finalScore)
            .finalPrediction(finalPrediction)
            .lgbmWeight(lgbmWeight)
            .xgboostWeight(xgboostWeight)
            .catboostWeight(catboostWeight)
            .threshold(threshold)
            .processingTimeMs(processingTimeMs)
            .predictionTime(LocalDateTime.now())
            .success(true)
            .build();
    }
    
    public static ModelPredictionResponse error(Long transactionId, String errorMessage) {
        return ModelPredictionResponse.builder()
            .transactionId(transactionId)
            .errorMessage(errorMessage)
            .success(false)
            .predictionTime(LocalDateTime.now())
            .build();
    }
}
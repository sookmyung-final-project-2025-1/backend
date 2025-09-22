package com.credit.card.fraud.detection.transactions.dto;

import com.credit.card.fraud.detection.transactions.entity.FraudDetectionResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "사기 탐지 결과 DTO")
public class FraudDetectionResultDto {

    @Schema(description = "탐지 결과 ID", example = "1")
    private Long id;

    @Schema(description = "LightGBM 점수", example = "0.234567")
    private BigDecimal lgbmScore;

    @Schema(description = "XGBoost 점수", example = "0.345678")
    private BigDecimal xgboostScore;

    @Schema(description = "CatBoost 점수", example = "0.456789")
    private BigDecimal catboostScore;

    @Schema(description = "최종 앙상블 점수", example = "0.312345")
    private BigDecimal finalScore;

    @Schema(description = "최종 예측 결과", example = "false")
    private Boolean finalPrediction;

    @Schema(description = "신뢰도 점수", example = "0.892")
    private BigDecimal confidenceScore;

    @Schema(description = "위험 등급", example = "MEDIUM")
    private String riskLevel;

    @Schema(description = "임계값", example = "0.5")
    private BigDecimal threshold;

    @Schema(description = "예측 시간", example = "2024-01-15T14:30:01")
    private LocalDateTime predictionTime;

    @Schema(description = "처리 시간 (밀리초)", example = "245")
    private Long processingTimeMs;

    @Schema(description = "모델 버전", example = "v2.1.0")
    private String modelVersion;

    @Schema(description = "생성 시간")
    private LocalDateTime createdAt;

    @Schema(description = "수정 시간")
    private LocalDateTime updatedAt;

    /**
     * FraudDetectionResult 엔티티를 FraudDetectionResultDto로 변환
     */
    public static FraudDetectionResultDto from(FraudDetectionResult result) {
        return FraudDetectionResultDto.builder()
                .id(result.getId())
                .lgbmScore(result.getLgbmScore())
                .xgboostScore(result.getXgboostScore())
                .catboostScore(result.getCatboostScore())
                .finalScore(result.getFinalScore())
                .finalPrediction(result.getFinalPrediction())
                .confidenceScore(result.getConfidenceScore())
                .riskLevel(result.getRiskLevel().name())
                .threshold(result.getThreshold())
                .predictionTime(result.getPredictionTime())
                .processingTimeMs(result.getProcessingTimeMs())
                .modelVersion(result.getModelVersion())
                .createdAt(result.getCreatedAt())
                .updatedAt(result.getUpdatedAt())
                .build();
    }
}
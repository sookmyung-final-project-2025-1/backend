package com.credit.card.fraud.detection.transactions.entity;

import com.credit.card.fraud.detection.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fraud_detection_results", indexes = {
        @Index(name = "idx_transaction_id", columnList = "transaction_id"),
        @Index(name = "idx_final_prediction", columnList = "finalPrediction"),
        @Index(name = "idx_final_score", columnList = "finalScore"),
        @Index(name = "idx_prediction_time", columnList = "predictionTime"),
        @Index(name = "idx_model_version", columnList = "modelVersion")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FraudDetectionResult extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    // 개별 모델 점수들
    @Column(name = "lgbm_score", precision = 8, scale = 6)
    private BigDecimal lgbmScore;

    @Column(name = "xgboost_score", precision = 8, scale = 6)
    private BigDecimal xgboostScore;

    @Column(name = "catboost_score", precision = 8, scale = 6)
    private BigDecimal catboostScore;

    // 앙상블 결과
    @Column(name = "final_score", nullable = false, precision = 8, scale = 6)
    private BigDecimal finalScore;

    @Column(name = "final_prediction", nullable = false)
    private Boolean finalPrediction;

    @Column(name = "confidence_score", precision = 8, scale = 6)
    private BigDecimal confidenceScore;

    // 모델 가중치 정보
    @Column(name = "lgbm_weight", precision = 5, scale = 4)
    private BigDecimal lgbmWeight;

    @Column(name = "xgboost_weight", precision = 5, scale = 4)
    private BigDecimal xgboostWeight;

    @Column(name = "catboost_weight", precision = 5, scale = 4)
    private BigDecimal catboostWeight;

    // 임계값 및 메타 정보
    @Column(name = "threshold", precision = 5, scale = 4)
    private BigDecimal threshold;

    @Column(name = "prediction_time", nullable = false)
    private LocalDateTime predictionTime;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    @Column(name = "model_version", length = 50)
    private String modelVersion;

    // JSON 형태로 저장되는 상세 정보
    @Column(name = "feature_importance", columnDefinition = "TEXT")
    private String featureImportance;

    @Column(name = "attention_scores", columnDefinition = "TEXT")
    private String attentionScores;

    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;

    // 추가 메타 데이터
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 20)
    @Builder.Default
    private RiskLevel riskLevel = RiskLevel.UNKNOWN;

    @Column(name = "is_reviewed", nullable = false)
    @Builder.Default
    private Boolean isReviewed = false;

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "review_comment", columnDefinition = "TEXT")
    private String reviewComment;

    public enum RiskLevel {
        LOW,        // 0.0 - 0.3
        MEDIUM,     // 0.3 - 0.7
        HIGH,       // 0.7 - 0.9
        CRITICAL,   // 0.9 - 1.0
        UNKNOWN
    }

    /**
     * 점수를 기반으로 위험도 계산
     */
    public void calculateRiskLevel() {
        if (finalScore == null) {
            this.riskLevel = RiskLevel.UNKNOWN;
            return;
        }

        double score = finalScore.doubleValue();
        if (score < 0.3) {
            this.riskLevel = RiskLevel.LOW;
        } else if (score < 0.7) {
            this.riskLevel = RiskLevel.MEDIUM;
        } else if (score < 0.9) {
            this.riskLevel = RiskLevel.HIGH;
        } else {
            this.riskLevel = RiskLevel.CRITICAL;
        }
    }

    /**
     * 검토 완료 처리
     */
    public void markAsReviewed(String reviewedBy, String comment) {
        this.isReviewed = true;
        this.reviewedBy = reviewedBy;
        this.reviewComment = comment;
    }

    /**
     * 고위험 거래 여부
     */
    public boolean isHighRisk() {
        return riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.CRITICAL;
    }

    /**
     * 임계값 초과 여부
     */
    public boolean exceedsThreshold() {
        return finalScore != null && threshold != null &&
                finalScore.compareTo(threshold) >= 0;
    }

    /**
     * 모델 합의 여부 (모든 모델이 유사한 점수를 가지는지)
     */
    public boolean hasModelConsensus() {
        if (lgbmScore == null || xgboostScore == null || catboostScore == null) {
            return false;
        }

        BigDecimal maxScore = lgbmScore.max(xgboostScore).max(catboostScore);
        BigDecimal minScore = lgbmScore.min(xgboostScore).min(catboostScore);
        BigDecimal difference = maxScore.subtract(minScore);

        // 0.2 이내의 차이면 합의로 간주
        return difference.compareTo(new BigDecimal("0.2")) <= 0;
    }

    /**
     * 신뢰도가 높은 예측인지 확인
     */
    public boolean isHighConfidence() {
        return confidenceScore != null &&
                confidenceScore.compareTo(new BigDecimal("0.8")) >= 0;
    }

    /**
     * 성능 지표 - 처리 시간이 빠른지 확인
     */
    public boolean isFastProcessing() {
        return processingTimeMs != null && processingTimeMs <= 100; // 100ms 이하
    }

    /**
     * 설명 가능한 결과인지 확인
     */
    public boolean hasExplanation() {
        return featureImportance != null && !featureImportance.trim().isEmpty();
    }

    /**
     * 위험도 레벨을 색상 코드로 반환 (UI용)
     */
    public String getRiskLevelColor() {
        return switch (riskLevel) {
            case LOW -> "#28a745";      // 녹색
            case MEDIUM -> "#ffc107";   // 노란색
            case HIGH -> "#fd7e14";     // 주황색
            case CRITICAL -> "#dc3545"; // 빨간색
            default -> "#6c757d";       // 회색
        };
    }

    /**
     * 각 모델의 예측 결과 요약
     */
    public String getModelPredictionSummary() {
        if (lgbmScore == null || xgboostScore == null || catboostScore == null) {
            return "Incomplete model predictions";
        }

        return String.format("LGBM: %.3f, XGBoost: %.3f, CatBoost: %.3f → Final: %.3f",
                lgbmScore.doubleValue(),
                xgboostScore.doubleValue(),
                catboostScore.doubleValue(),
                finalScore.doubleValue());
    }

    /**
     * 가중치 정보 요약
     */
    public String getWeightsSummary() {
        if (lgbmWeight == null || xgboostWeight == null || catboostWeight == null) {
            return "Unknown weights";
        }

        return String.format("LGBM: %.2f, XGBoost: %.2f, CatBoost: %.2f",
                lgbmWeight.doubleValue(),
                xgboostWeight.doubleValue(),
                catboostWeight.doubleValue());
    }
}
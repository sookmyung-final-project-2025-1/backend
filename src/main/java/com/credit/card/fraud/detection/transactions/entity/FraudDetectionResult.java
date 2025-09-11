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
    @Index(name = "idx_prediction_time", columnList = "predictionTime"),
    @Index(name = "idx_final_prediction", columnList = "finalPrediction")
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

    @Column(nullable = false, precision = 8, scale = 6)
    private BigDecimal lgbmScore;

    @Column(nullable = false, precision = 8, scale = 6)
    private BigDecimal xgboostScore;

    @Column(nullable = false, precision = 8, scale = 6)
    private BigDecimal catboostScore;

    @Column(nullable = false, precision = 8, scale = 6)
    private BigDecimal finalScore;

    @Builder.Default
    private Boolean finalPrediction = false;

    @Column(nullable = false, precision = 8, scale = 6)
    @Builder.Default
    private BigDecimal confidenceScore = BigDecimal.ZERO;

    @Column(nullable = false, precision = 4, scale = 3)
    @Builder.Default
    private BigDecimal lgbmWeight = new BigDecimal("0.333");

    @Column(nullable = false, precision = 4, scale = 3)
    @Builder.Default
    private BigDecimal xgboostWeight = new BigDecimal("0.333");

    @Column(nullable = false, precision = 4, scale = 3)
    @Builder.Default
    private BigDecimal catboostWeight = new BigDecimal("0.334");

    @Column(nullable = false, precision = 4, scale = 3)
    @Builder.Default
    private BigDecimal threshold = new BigDecimal("0.5");

    @Column(nullable = false)
    private LocalDateTime predictionTime;

    @Column(precision = 10, scale = 0)
    private Long processingTimeMs;

    @Column(columnDefinition = "TEXT")
    private String featureImportance;

    @Column(columnDefinition = "TEXT")
    private String attentionScores;

    @Column
    private String modelVersion;

    public void updatePrediction(BigDecimal threshold) {
        this.threshold = threshold;
        this.finalPrediction = this.finalScore.compareTo(threshold) > 0;
    }

    public void updateWeights(BigDecimal lgbmWeight, BigDecimal xgboostWeight, BigDecimal catboostWeight) {
        this.lgbmWeight = lgbmWeight;
        this.xgboostWeight = xgboostWeight;
        this.catboostWeight = catboostWeight;
        
        // 최종 점수 재계산
        this.finalScore = lgbmScore.multiply(lgbmWeight)
            .add(xgboostScore.multiply(xgboostWeight))
            .add(catboostScore.multiply(catboostWeight));
        
        // 예측 결과 업데이트
        this.finalPrediction = this.finalScore.compareTo(this.threshold) > 0;
    }
}
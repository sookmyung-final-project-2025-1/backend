package com.credit.card.fraud.detection.transactions.repository;

import com.credit.card.fraud.detection.transactions.entity.FraudDetectionResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FraudDetectionResultRepository extends JpaRepository<FraudDetectionResult, Long> {

    Optional<FraudDetectionResult> findByTransaction_Id(Long transactionId);

    List<FraudDetectionResult> findByPredictionTimeBetweenOrderByPredictionTimeDesc(
            LocalDateTime startTime, LocalDateTime endTime);

    @Query("SELECT AVG(fdr.confidenceScore) FROM FraudDetectionResult fdr " +
            "WHERE fdr.predictionTime >= :startTime AND fdr.predictionTime <= :endTime")
    BigDecimal averageConfidenceScore(@Param("startTime") LocalDateTime startTime,
                                      @Param("endTime") LocalDateTime endTime);

    @Query("SELECT fdr FROM FraudDetectionResult fdr " +
            "WHERE fdr.finalScore >= :minScore AND fdr.finalScore <= :maxScore " +
            "ORDER BY fdr.predictionTime DESC")
    Page<FraudDetectionResult> findByScoreRange(@Param("minScore") BigDecimal minScore,
                                                @Param("maxScore") BigDecimal maxScore,
                                                Pageable pageable);

    @Query("SELECT COUNT(fdr) FROM FraudDetectionResult fdr " +
            "WHERE fdr.predictionTime >= :startTime AND fdr.predictionTime <= :endTime " +
            "AND fdr.finalPrediction = true")
    Long countFraudPredictionsInTimeWindow(@Param("startTime") LocalDateTime startTime,
                                           @Param("endTime") LocalDateTime endTime);

    @Query("SELECT AVG(fdr.processingTimeMs) FROM FraudDetectionResult fdr " +
            "WHERE fdr.predictionTime >= :startTime AND fdr.predictionTime <= :endTime")
    Double averageProcessingTime(@Param("startTime") LocalDateTime startTime,
                                 @Param("endTime") LocalDateTime endTime);

    @Query(value = "SELECT AVG(sub.processing_time_ms) FROM (" +
            "SELECT processing_time_ms, " +
            "ROW_NUMBER() OVER (ORDER BY processing_time_ms) as rn, " +
            "COUNT(*) OVER () as cnt " +
            "FROM fraud_detection_results " +
            "WHERE prediction_time >= :startTime AND prediction_time <= :endTime " +
            "AND processing_time_ms IS NOT NULL" +
            ") sub " +
            "WHERE sub.rn IN (FLOOR((sub.cnt + 1) / 2), CEIL((sub.cnt + 1) / 2))",
            nativeQuery = true)
    Double medianProcessingTime(@Param("startTime") LocalDateTime startTime,
                                @Param("endTime") LocalDateTime endTime);

    @Query(value = "SELECT processing_time_ms FROM " +
            "(SELECT processing_time_ms, " +
            " ROW_NUMBER() OVER (ORDER BY processing_time_ms DESC) as rn, " +
            " COUNT(*) OVER () as total_count " +
            " FROM fraud_detection_results " +
            " WHERE prediction_time >= :startTime AND prediction_time <= :endTime " +
            " AND processing_time_ms IS NOT NULL" +
            ") ranked " +
            "WHERE rn = FLOOR(total_count * 0.05) + 1",
            nativeQuery = true)
    Double p95ProcessingTime(@Param("startTime") LocalDateTime startTime,
                             @Param("endTime") LocalDateTime endTime);

    @Query(value = "SELECT DATE_FORMAT(prediction_time, '%Y-%m-%d %H:00:00') as hour, " +
            "AVG(confidence_score) as avgConfidence, " +
            "COUNT(*) as totalCount, " +
            "AVG(processing_time_ms) as avgProcessingTime " +
            "FROM fraud_detection_results " +
            "WHERE prediction_time >= :startTime AND prediction_time <= :endTime " +
            "GROUP BY DATE_FORMAT(prediction_time, '%Y-%m-%d %H:00:00') " +
            "ORDER BY hour",
            nativeQuery = true)
    List<Object[]> getHourlyStats(@Param("startTime") LocalDateTime startTime,
                                  @Param("endTime") LocalDateTime endTime);

    List<FraudDetectionResult> findTop1000ByOrderByPredictionTimeDesc();

    @Query("SELECT fdr FROM FraudDetectionResult fdr " +
            "JOIN FETCH fdr.transaction t " +
            "WHERE fdr.finalScore >= :threshold " +
            "AND fdr.predictionTime >= :startTime " +
            "ORDER BY fdr.finalScore DESC")
    List<FraudDetectionResult> findHighRiskTransactions(@Param("threshold") BigDecimal threshold,
                                                        @Param("startTime") LocalDateTime startTime);

    List<FraudDetectionResult> findByFinalScoreGreaterThanOrderByFinalScoreDesc(BigDecimal threshold);

    List<FraudDetectionResult> findByRiskLevelOrderByCreatedAtDesc(FraudDetectionResult.RiskLevel riskLevel);

    @Query("SELECT fdr FROM FraudDetectionResult fdr " +
            "WHERE fdr.predictionTime BETWEEN :startTime AND :endTime " +
            "ORDER BY fdr.finalScore DESC")
    List<FraudDetectionResult> findResultsInPeriod(@Param("startTime") LocalDateTime startTime,
                                                   @Param("endTime") LocalDateTime endTime);

    @Query("SELECT AVG(fdr.processingTimeMs) FROM FraudDetectionResult fdr " +
            "WHERE fdr.predictionTime >= :since")
    Double getAverageProcessingTime(@Param("since") LocalDateTime since);

    @Query("SELECT fdr.modelVersion, COUNT(fdr) FROM FraudDetectionResult fdr " +
            "GROUP BY fdr.modelVersion " +
            "ORDER BY COUNT(fdr) DESC")
    List<Object[]> getModelVersionStats();

    @Query("SELECT COUNT(fdr) FROM FraudDetectionResult fdr " +
            "WHERE fdr.predictionTime >= :startTime AND fdr.predictionTime <= :endTime " +
            "AND fdr.finalPrediction = true")
    Long countFraudPredictions(@Param("startTime") LocalDateTime startTime,
                               @Param("endTime") LocalDateTime endTime);

}
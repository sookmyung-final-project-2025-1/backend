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

    @Query("SELECT AVG(CAST(fdr.processingTimeMs AS double)) FROM FraudDetectionResult fdr " +
           "WHERE fdr.predictionTime >= :startTime AND fdr.predictionTime <= :endTime")
    Double averageProcessingTime(@Param("startTime") LocalDateTime startTime, 
                               @Param("endTime") LocalDateTime endTime);

    @Query("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY fdr.processingTimeMs) " +
           "FROM FraudDetectionResult fdr " +
           "WHERE fdr.predictionTime >= :startTime AND fdr.predictionTime <= :endTime")
    Double medianProcessingTime(@Param("startTime") LocalDateTime startTime, 
                              @Param("endTime") LocalDateTime endTime);

    @Query("SELECT PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY fdr.processingTimeMs) " +
           "FROM FraudDetectionResult fdr " +
           "WHERE fdr.predictionTime >= :startTime AND fdr.predictionTime <= :endTime")
    Double p95ProcessingTime(@Param("startTime") LocalDateTime startTime, 
                           @Param("endTime") LocalDateTime endTime);

    @Query("SELECT DATE_TRUNC('hour', fdr.predictionTime) as hour, " +
           "AVG(fdr.confidenceScore) as avgConfidence, " +
           "COUNT(fdr) as totalCount, " +
           "AVG(CAST(fdr.processingTimeMs AS double)) as avgProcessingTime " +
           "FROM FraudDetectionResult fdr " +
           "WHERE fdr.predictionTime >= :startTime AND fdr.predictionTime <= :endTime " +
           "GROUP BY DATE_TRUNC('hour', fdr.predictionTime) " +
           "ORDER BY hour")
    List<Object[]> getHourlyStats(@Param("startTime") LocalDateTime startTime, 
                                @Param("endTime") LocalDateTime endTime);

    List<FraudDetectionResult> findTop1000ByOrderByPredictionTimeDesc();

    @Query("SELECT fdr FROM FraudDetectionResult fdr " +
           "JOIN fdr.transaction t " +
           "WHERE fdr.finalScore >= :threshold " +
           "AND fdr.predictionTime >= :startTime " +
           "ORDER BY fdr.finalScore DESC")
    List<FraudDetectionResult> findHighRiskTransactions(@Param("threshold") BigDecimal threshold, 
                                                       @Param("startTime") LocalDateTime startTime);
}
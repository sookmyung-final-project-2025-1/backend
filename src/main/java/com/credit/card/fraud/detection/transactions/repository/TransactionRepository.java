package com.credit.card.fraud.detection.transactions.repository;

import com.credit.card.fraud.detection.transactions.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByVirtualTimeBetweenOrderByVirtualTime(
        LocalDateTime startTime, LocalDateTime endTime);

    @Query("SELECT t FROM Transaction t WHERE t.virtualTime >= :startTime AND t.virtualTime < :endTime ORDER BY t.virtualTime")
    List<Transaction> findForTimeWindow(@Param("startTime") LocalDateTime startTime, 
                                      @Param("endTime") LocalDateTime endTime);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.virtualTime >= :startTime AND t.virtualTime < :endTime")
    Long countTransactionsInTimeWindow(@Param("startTime") LocalDateTime startTime, 
                                     @Param("endTime") LocalDateTime endTime);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.virtualTime >= :startTime AND t.virtualTime < :endTime AND t.isFraud = true")
    Long countFraudTransactionsInTimeWindow(@Param("startTime") LocalDateTime startTime, 
                                          @Param("endTime") LocalDateTime endTime);

    @Query("SELECT COUNT(DISTINCT t.userId) FROM Transaction t WHERE t.virtualTime >= :startTime AND t.virtualTime < :endTime")
    Long countUniqueUsersInTimeWindow(@Param("startTime") LocalDateTime startTime, 
                                    @Param("endTime") LocalDateTime endTime);

    @Query("SELECT AVG(t.amount) FROM Transaction t WHERE t.virtualTime >= :startTime AND t.virtualTime < :endTime")
    Double averageTransactionAmountInTimeWindow(@Param("startTime") LocalDateTime startTime,
                                              @Param("endTime") LocalDateTime endTime);

    List<Transaction> findByUserIdOrderByVirtualTimeDesc(String userId);

    List<Transaction> findByMerchantOrderByVirtualTimeDesc(String merchant);

    Page<Transaction> findByAmountBetween(BigDecimal minAmount, BigDecimal maxAmount, Pageable pageable);

    Page<Transaction> findByIsFraud(Boolean isFraud, Pageable pageable);

    Page<Transaction> findByMerchantCategoryContainingIgnoreCase(String category, Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE " +
           "(:userId IS NULL OR t.userId = :userId) AND " +
           "(:merchant IS NULL OR t.merchant LIKE %:merchant%) AND " +
           "(:category IS NULL OR t.merchantCategory LIKE %:category%) AND " +
           "(:minAmount IS NULL OR t.amount >= :minAmount) AND " +
           "(:maxAmount IS NULL OR t.amount <= :maxAmount) AND " +
           "(:isFraud IS NULL OR t.isFraud = :isFraud) AND " +
           "(t.virtualTime >= :startTime AND t.virtualTime <= :endTime) " +
           "ORDER BY t.virtualTime DESC")
    Page<Transaction> findWithFilters(
        @Param("userId") String userId,
        @Param("merchant") String merchant,
        @Param("category") String category,
        @Param("minAmount") BigDecimal minAmount,
        @Param("maxAmount") BigDecimal maxAmount,
        @Param("isFraud") Boolean isFraud,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime,
        Pageable pageable
    );

    Optional<Transaction> findByExternalTransactionId(String externalTransactionId);

    @Query(value = "SELECT DATE(virtual_time) as date, COUNT(*) as count, " +
            "SUM(CASE WHEN is_fraud = true THEN 1 ELSE 0 END) as fraudCount " +
            "FROM transactions " +
            "WHERE virtual_time >= :startDate AND virtual_time <= :endDate " +
            "GROUP BY DATE(virtual_time) " +
            "ORDER BY DATE(virtual_time)",
            nativeQuery = true)
    List<Object[]> getDailyTransactionStats(@Param("startDate") LocalDateTime startDate,
                                            @Param("endDate") LocalDateTime endDate);

    // 사기 거래 금액 합계
    @Query("SELECT SUM(t.amount) FROM Transaction t WHERE t.virtualTime >= :startTime AND t.virtualTime < :endTime AND t.isFraud = true")
    Double sumFraudTransactionAmounts(@Param("startTime") LocalDateTime startTime,
                                    @Param("endTime") LocalDateTime endTime);

    @Query("SELECT COUNT(DISTINCT t.userId) FROM Transaction t " +
            "WHERE t.userId NOT IN (" +
            "  SELECT DISTINCT t2.userId FROM Transaction t2 " +
            "  WHERE t2.transactionTime < :startTime" +
            ") AND t.transactionTime >= :startTime AND t.transactionTime <= :endTime")
    Long countNewUsersInPeriod(@Param("startTime") LocalDateTime startTime,
                                @Param("endTime") LocalDateTime endTime);

    // 사용자별 거래 조회
    List<Transaction> findByUserIdOrderByTransactionTimeDesc(String userId, Pageable pageable);

    // 고위험 거래 조회 (탐지 결과와 조인)
    @Query("SELECT t FROM Transaction t " +
            "JOIN FraudDetectionResult fdr ON fdr.transaction = t " +
            "WHERE fdr.finalScore >= :minScore " +
            "ORDER BY fdr.finalScore DESC")
    List<Transaction> findHighRiskTransactions(@Param("minScore") BigDecimal minScore, Pageable pageable);

    // 골드 라벨 거래 조회
    List<Transaction> findByGoldLabelIsNotNullAndGoldLabel(Boolean goldLabel, Pageable pageable);

    // 기간별 거래 통계
    @Query("SELECT COUNT(t) FROM Transaction t " +
            "WHERE t.transactionTime BETWEEN :startTime AND :endTime")
    Long countTransactionsInPeriod(@Param("startTime") LocalDateTime startTime,
                                   @Param("endTime") LocalDateTime endTime);

    // 사기 거래 통계
    @Query("SELECT COUNT(t) FROM Transaction t " +
            "WHERE t.isFraud = true AND t.transactionTime BETWEEN :startTime AND :endTime")
    Long countFraudTransactionsInPeriod(@Param("startTime") LocalDateTime startTime,
                                        @Param("endTime") LocalDateTime endTime);

    // 최근 처리된 거래들
    @Query("SELECT t FROM Transaction t " +
            "WHERE t.status = 'PROCESSED' " +
            "AND t.updatedAt >= :since " +
            "ORDER BY t.updatedAt DESC")
    List<Transaction> findRecentlyProcessed(@Param("since") LocalDateTime since);

    // 머천트별 거래 통계
    @Query("SELECT t.merchant, COUNT(t), SUM(t.amount) " +
            "FROM Transaction t " +
            "WHERE t.transactionTime BETWEEN :startTime AND :endTime " +
            "GROUP BY t.merchant " +
            "ORDER BY COUNT(t) DESC")
    List<Object[]> getMerchantStatistics(@Param("startTime") LocalDateTime startTime,
                                         @Param("endTime") LocalDateTime endTime);

    // 상태별 거래 조회
    Page<Transaction> findByStatus(Transaction.TransactionStatus status, Pageable pageable);

    // 배치 처리용 최적화된 쿼리 (detectionResults만)
    @Query("SELECT t FROM Transaction t " +
           "LEFT JOIN FETCH t.detectionResults " +
           "WHERE t.status = :status")
    List<Transaction> findByStatusWithDetectionResults(@Param("status") Transaction.TransactionStatus status, Pageable pageable);

    // 배치 처리용 최적화된 쿼리 (reports만)
    @Query("SELECT t FROM Transaction t " +
           "LEFT JOIN FETCH t.reports " +
           "WHERE t.status = :status")
    List<Transaction> findByStatusWithReports(@Param("status") Transaction.TransactionStatus status, Pageable pageable);

    // 배치 처리용 PENDING 거래 ID만 조회 (페이징 지원)
    @Query("SELECT t.id FROM Transaction t " +
           "WHERE t.status = 'PENDING' " +
           "ORDER BY t.id")
    List<Long> findPendingTransactionIds(Pageable pageable);

    // ID 리스트로 연관 데이터와 함께 조회 (detectionResults만)
    @Query("SELECT DISTINCT t FROM Transaction t " +
           "LEFT JOIN FETCH t.detectionResults " +
           "WHERE t.id IN :ids " +
           "ORDER BY t.id")
    List<Transaction> findTransactionsWithAssociationsByIds(@Param("ids") List<Long> ids);

    // ID 리스트로 reports만 함께 조회
    @Query("SELECT DISTINCT t FROM Transaction t " +
           "LEFT JOIN FETCH t.reports " +
           "WHERE t.id IN :ids " +
           "ORDER BY t.id")
    List<Transaction> findTransactionsWithReportsByIds(@Param("ids") List<Long> ids);

    // 기본 Transaction 엔티티만 조회 (연관관계 없이)
    @Query("SELECT t FROM Transaction t " +
           "WHERE t.id IN :ids " +
           "ORDER BY t.id")
    List<Transaction> findTransactionsByIds(@Param("ids") List<Long> ids);

    // EntityGraph를 사용한 안전한 다중 연관관계 로드 (필요시 사용)
    @EntityGraph(attributePaths = {"detectionResults"})
    @Query("SELECT t FROM Transaction t WHERE t.id IN :ids ORDER BY t.id")
    List<Transaction> findTransactionsWithDetectionResultsByIds(@Param("ids") List<Long> ids);

    @EntityGraph(attributePaths = {"reports"})
    @Query("SELECT t FROM Transaction t WHERE t.id IN :ids ORDER BY t.id")
    List<Transaction> findTransactionsWithReportsByIdsEntityGraph(@Param("ids") List<Long> ids);

    // 배치 처리용 PENDING 거래 조회 (기본 엔티티만)
    @Query("SELECT t FROM Transaction t " +
           "WHERE t.status = 'PENDING' " +
           "ORDER BY t.id")
    List<Transaction> findPendingTransactionsWithAssociations(Pageable pageable);

    // 배치 처리용 ID 리스트로 조회
    @Query("SELECT t FROM Transaction t " +
           "LEFT JOIN FETCH t.detectionResults " +
           "WHERE t.id IN :ids")
    List<Transaction> findByIdsWithDetectionResults(@Param("ids") List<Long> ids);

    // 상태별 거래 개수 조회
    Long countByStatus(Transaction.TransactionStatus status);

    // 상태별 거래 삭제
    @Modifying
    Long deleteByStatus(Transaction.TransactionStatus status);
}
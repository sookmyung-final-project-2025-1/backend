package com.credit.card.fraud.detection.transactions.repository;

import com.credit.card.fraud.detection.transactions.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    // 상태별 거래 개수 조회
    Long countByStatus(Transaction.TransactionStatus status);

    // 상태별 거래 삭제
    @Modifying
    Long deleteByStatus(Transaction.TransactionStatus status);
}
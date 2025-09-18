package com.credit.card.fraud.detection.transactions.repository;

import com.credit.card.fraud.detection.transactions.entity.Transaction;
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
}
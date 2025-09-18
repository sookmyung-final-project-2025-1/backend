package com.credit.card.fraud.detection.transactions.repository;

import com.credit.card.fraud.detection.transactions.entity.UserReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserReportRepository extends JpaRepository<UserReport, Long> {

    List<UserReport> findByTransaction_IdOrderByCreatedAtDesc(Long transactionId);

    // 별칭 메서드 (기존 호환성 유지)
    default List<UserReport> findByTransactionIdOrderByCreatedAtDesc(Long transactionId) {
        return findByTransaction_IdOrderByCreatedAtDesc(transactionId);
    }

    Page<UserReport> findByStatusOrderByCreatedAtDesc(UserReport.ReportStatus status, Pageable pageable);

    Page<UserReport> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<UserReport> findByReportedByOrderByCreatedAtDesc(String reportedBy);

    Page<UserReport> findByStatusAndReviewedBy(UserReport.ReportStatus status, String reviewedBy, Pageable pageable);

    @Query("SELECT COUNT(ur) FROM UserReport ur WHERE ur.status = :status")
    Long countByStatus(@Param("status") UserReport.ReportStatus status);

    @Query("SELECT ur FROM UserReport ur " +
           "WHERE ur.status = :pendingStatus " +
           "ORDER BY ur.createdAt ASC")
    List<UserReport> findPendingReportsOrderByCreatedAt(@Param("pendingStatus") UserReport.ReportStatus pendingStatus);

    @Query("SELECT COUNT(ur) FROM UserReport ur " +
           "WHERE ur.status = :approvedStatus " +
           "AND ur.isFraudConfirmed = true")
    Long countConfirmedFraudReports(@Param("approvedStatus") UserReport.ReportStatus approvedStatus);

    List<UserReport> findByTransaction_Id(Long transactionId);

    // 기간별 통계 메서드들
    @Query("SELECT COUNT(ur) FROM UserReport ur WHERE ur.createdAt BETWEEN :startDate AND :endDate")
    Long countReportsInPeriod(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(ur) FROM UserReport ur WHERE ur.status = :approvedStatus AND ur.createdAt BETWEEN :startDate AND :endDate")
    Long countApprovedReportsInPeriod(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate, @Param("approvedStatus") UserReport.ReportStatus approvedStatus);

    @Query("SELECT COUNT(ur) FROM UserReport ur WHERE ur.status = :rejectedStatus AND ur.createdAt BETWEEN :startDate AND :endDate")
    Long countRejectedReportsInPeriod(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate, @Param("rejectedStatus") UserReport.ReportStatus rejectedStatus);

    @Query("SELECT COUNT(ur) FROM UserReport ur WHERE ur.status = :pendingStatus AND ur.createdAt BETWEEN :startDate AND :endDate")
    Long countPendingReportsInPeriod(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate, @Param("pendingStatus") UserReport.ReportStatus pendingStatus);

    @Query("SELECT COUNT(ur) FROM UserReport ur WHERE ur.status = :underReviewStatus AND ur.createdAt BETWEEN :startDate AND :endDate")
    Long countUnderReviewReportsInPeriod(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate, @Param("underReviewStatus") UserReport.ReportStatus underReviewStatus);

    @Query("SELECT COUNT(ur) FROM UserReport ur " +
        "WHERE ur.createdAt >= :startTime AND ur.createdAt <= :endTime " +
        "AND ur.status = 'APPROVED' AND ur.isFraudConfirmed = true")
    Long countConfirmedFraud(@Param("startTime") LocalDateTime startTime,
                            @Param("endTime") LocalDateTime endTime);
}
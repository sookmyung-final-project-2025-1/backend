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

    // 거래별 신고 조회
    List<UserReport> findByTransaction_IdOrderByCreatedAtDesc(Long transactionId);

    // 별칭 메서드 (기존 호환성 유지)
    default List<UserReport> findByTransactionIdOrderByCreatedAtDesc(Long transactionId) {
        return findByTransaction_IdOrderByCreatedAtDesc(transactionId);
    }

    // 상태별 신고 조회
    Page<UserReport> findByStatusOrderByCreatedAtDesc(UserReport.ReportStatus status, Pageable pageable);

    // 전체 신고 조회
    Page<UserReport> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // 신고자별 조회
    List<UserReport> findByReportedByOrderByCreatedAtDesc(String reportedBy);

    // 상태와 검토자별 조회
    Page<UserReport> findByStatusAndReviewedBy(UserReport.ReportStatus status, String reviewedBy, Pageable pageable);

    // 상태별 카운트
    @Query("SELECT COUNT(ur) FROM UserReport ur WHERE ur.status = :status")
    Long countByStatus(@Param("status") UserReport.ReportStatus status);

    // 대기 중인 신고 조회 (오래된 순)
    @Query("SELECT ur FROM UserReport ur " +
            "WHERE ur.status = :pendingStatus " +
            "ORDER BY ur.createdAt ASC")
    List<UserReport> findPendingReportsOrderByCreatedAt(@Param("pendingStatus") UserReport.ReportStatus pendingStatus);

    // 확정된 사기 신고 카운트
    @Query("SELECT COUNT(ur) FROM UserReport ur " +
            "WHERE ur.status = :approvedStatus " +
            "AND ur.isFraudConfirmed = true")
    Long countConfirmedFraudReports(@Param("approvedStatus") UserReport.ReportStatus approvedStatus);

    // 기간별 통계 쿼리들
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

    // 골드 라벨 통계
    @Query("SELECT COUNT(ur) FROM UserReport ur " +
            "WHERE ur.createdAt >= :startTime AND ur.createdAt <= :endTime " +
            "AND ur.status = 'APPROVED' AND ur.isFraudConfirmed = true")
    Long countConfirmedFraud(@Param("startTime") LocalDateTime startTime,
                             @Param("endTime") LocalDateTime endTime);

    // 신고자와 기간별 조회
    @Query("SELECT ur FROM UserReport ur " +
            "WHERE ur.reportedBy = :reportedBy " +
            "AND ur.createdAt BETWEEN :startDate AND :endDate " +
            "ORDER BY ur.createdAt DESC")
    List<UserReport> findByReportedByAndPeriod(@Param("reportedBy") String reportedBy,
                                               @Param("startDate") LocalDateTime startDate,
                                               @Param("endDate") LocalDateTime endDate);

    // 복합 조건 검색
    @Query("SELECT ur FROM UserReport ur " +
            "WHERE (:status IS NULL OR ur.status = :status) " +
            "AND (:reportedBy IS NULL OR ur.reportedBy = :reportedBy) " +
            "AND (:startDate IS NULL OR ur.createdAt >= :startDate) " +
            "AND (:endDate IS NULL OR ur.createdAt <= :endDate) " +
            "ORDER BY ur.createdAt DESC")
    Page<UserReport> findByFilters(@Param("status") UserReport.ReportStatus status,
                                   @Param("reportedBy") String reportedBy,
                                   @Param("startDate") LocalDateTime startDate,
                                   @Param("endDate") LocalDateTime endDate,
                                   Pageable pageable);

    // 최근 활동 조회
    @Query("SELECT ur FROM UserReport ur " +
            "WHERE ur.updatedAt >= :since " +
            "ORDER BY ur.updatedAt DESC")
    List<UserReport> findRecentActivity(@Param("since") LocalDateTime since);
}
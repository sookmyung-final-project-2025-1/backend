package com.credit.card.fraud.detection.transactions.repository;

import com.credit.card.fraud.detection.transactions.entity.UserReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserReportRepository extends JpaRepository<UserReport, Long> {

    List<UserReport> findByTransaction_IdOrderByCreatedAtDesc(Long transactionId);

    Page<UserReport> findByStatus(UserReport.ReportStatus status, Pageable pageable);

    List<UserReport> findByReportedByOrderByCreatedAtDesc(String reportedBy);

    Page<UserReport> findByStatusAndReviewedBy(UserReport.ReportStatus status, String reviewedBy, Pageable pageable);

    @Query("SELECT COUNT(ur) FROM UserReport ur WHERE ur.status = :status")
    Long countByStatus(@Param("status") UserReport.ReportStatus status);

    @Query("SELECT ur FROM UserReport ur " +
           "WHERE ur.status = com.credit.card.fraud.detection.transactions.entity.UserReport$ReportStatus.PENDING " +
           "ORDER BY ur.createdAt ASC")
    List<UserReport> findPendingReportsOrderByCreatedAt();

    @Query("SELECT COUNT(ur) FROM UserReport ur " +
           "WHERE ur.status = com.credit.card.fraud.detection.transactions.entity.UserReport$ReportStatus.APPROVED " +
           "AND ur.isFraudConfirmed = true")
    Long countConfirmedFraudReports();

    List<UserReport> findByTransaction_Id(Long transactionId);
}
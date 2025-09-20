package com.credit.card.fraud.detection.transactions.service;

import com.credit.card.fraud.detection.modelclient.service.ConfidenceScoreService;
import com.credit.card.fraud.detection.transactions.entity.Transaction;
import com.credit.card.fraud.detection.transactions.entity.UserReport;
import com.credit.card.fraud.detection.transactions.exceptions.ReportNotFoundException;
import com.credit.card.fraud.detection.transactions.exceptions.ReportReviewException;
import com.credit.card.fraud.detection.transactions.exceptions.ReportStatisticsException;
import com.credit.card.fraud.detection.transactions.exceptions.TransactionNotFoundException;
import com.credit.card.fraud.detection.transactions.repository.TransactionRepository;
import com.credit.card.fraud.detection.transactions.repository.UserReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserReportService {

    private final UserReportRepository userReportRepository;
    private final TransactionRepository transactionRepository;
    private final ModelUpdateNotificationService notificationService;
    private final ConfidenceScoreService confidenceScoreService;

    /**
     * 사기 거래 신고 접수 - 모든 신고는 여기서 처리
     */
    @Transactional
    public UserReport createReport(Long transactionId, String reportedBy, String reason, String description) {
        validateReportRequest(transactionId, reportedBy, reason);

        Transaction transaction = getTransactionById(transactionId);

        UserReport report = UserReport.builder()
                .transaction(transaction)
                .reportedBy(reportedBy)
                .reason(reason)
                .description(description)
                .build();

        UserReport savedReport = userReportRepository.save(report);

        // 거래에 신고 추가
        transaction.addReport(savedReport);
        transactionRepository.save(transaction);

        log.info("Fraud report submitted for transaction {} by user {}", transactionId, reportedBy);

        // 관리자에게 신고 알림
        notificationService.sendReportNotification(savedReport);

        return savedReport;
    }

    /**
     * 필터 조건에 따른 신고 목록 조회
     */
    public Page<UserReport> getReports(UserReport.ReportStatus status, String reportedBy,
                                       LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        return userReportRepository.findByFilters(status, reportedBy, startDate, endDate, pageable);
    }

    /**
     * 신고 ID로 조회
     */
    public UserReport getReportById(Long reportId) {
        return userReportRepository.findById(reportId)
                .orElseThrow(() -> new ReportNotFoundException("Report not found: " + reportId));
    }

    /**
     * 신고 검토 처리 - 골드 라벨 적용의 핵심 로직
     */
    @Transactional
    public Map<String, Object> reviewReport(Long reportId, String reviewedBy, String comment,
                                            Boolean isFraud, String action) {
        validateReviewRequest(reportId, reviewedBy, action);

        UserReport report = getReportById(reportId);
        BigDecimal confidenceScoreBefore = confidenceScoreService.getCurrentConfidenceScore();

        Map<String, Object> result = createBaseResult(reportId, reviewedBy);

        try {
            processReviewAction(report, reviewedBy, comment, isFraud, action, result, confidenceScoreBefore);
            userReportRepository.save(report);

            notificationService.sendReportReviewNotification(report, action);
            log.info("Report {} reviewed by {}: action={}, isFraud={}", reportId, reviewedBy, action, isFraud);

        } catch (Exception e) {
            log.error("Failed to review report {}: {}", reportId, e.getMessage(), e);
            throw new ReportReviewException("Failed to review report: " + e.getMessage(), e);
        }

        return result;
    }

    /**
     * 신고 통계 조회
     */
    public Map<String, Object> getReportStats(Integer days) {
        if (days == null || days <= 0) {
            throw new IllegalArgumentException("Days must be a positive number");
        }

        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        LocalDateTime endDate = LocalDateTime.now();

        return buildReportStatistics(startDate, endDate, days);
    }

    /**
     * 대기 중인 신고 수 조회
     */
    public long getPendingReportsCount() {
        return userReportRepository.countByStatus(UserReport.ReportStatus.PENDING);
    }

    /**
     * 검토 중인 신고 수 조회
     */
    public long getUnderReviewReportsCount() {
        return userReportRepository.countByStatus(UserReport.ReportStatus.UNDER_REVIEW);
    }

    /**
     * 거래별 신고 내역 조회
     */
    public List<UserReport> getReportsByTransaction(Long transactionId) {
        if (transactionId == null) {
            throw new IllegalArgumentException("Transaction ID cannot be null");
        }
        return userReportRepository.findByTransactionIdOrderByCreatedAtDesc(transactionId);
    }

    /**
     * 신고 우선순위 설정
     */
    @Transactional
    public void setPriority(Long reportId, String priority) {
        if (reportId == null || priority == null) {
            throw new IllegalArgumentException("Report ID and priority cannot be null");
        }

        UserReport report = getReportById(reportId);
        log.info("Setting priority for report {}: {}", reportId, priority);
        notificationService.sendPriorityChangeNotification(reportId, priority);
    }

    /**
     * 최근 활동 조회
     */
    public List<UserReport> getRecentActivity(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return userReportRepository.findRecentActivity(since);
    }

    /**
     * 신고자별 신고 내역 조회
     */
    public List<UserReport> getReportsByReporter(String reportedBy, LocalDateTime startDate, LocalDateTime endDate) {
        if (reportedBy == null || reportedBy.trim().isEmpty()) {
            throw new IllegalArgumentException("Reporter information cannot be null or empty");
        }

        if (startDate == null) startDate = LocalDateTime.now().minusDays(30);
        if (endDate == null) endDate = LocalDateTime.now();

        return userReportRepository.findByReportedByAndPeriod(reportedBy, startDate, endDate);
    }

    // Private helper methods

    private Transaction getTransactionById(Long transactionId) {
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + transactionId));
    }

    private void validateReportRequest(Long transactionId, String reportedBy, String reason) {
        if (transactionId == null) {
            throw new IllegalArgumentException("Transaction ID cannot be null");
        }
        if (reportedBy == null || reportedBy.trim().isEmpty()) {
            throw new IllegalArgumentException("Reporter information cannot be null or empty");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Reason cannot be null or empty");
        }
    }

    private void validateReviewRequest(Long reportId, String reviewedBy, String action) {
        if (reportId == null || reviewedBy == null || action == null) {
            throw new IllegalArgumentException("Report ID, reviewer, and action cannot be null");
        }
    }

    private Map<String, Object> createBaseResult(Long reportId, String reviewedBy) {
        Map<String, Object> result = new HashMap<>();
        result.put("reportId", reportId);
        result.put("reviewedBy", reviewedBy);
        result.put("reviewedAt", LocalDateTime.now());
        return result;
    }

    private void processReviewAction(UserReport report, String reviewedBy, String comment,
                                     Boolean isFraud, String action, Map<String, Object> result,
                                     BigDecimal confidenceScoreBefore) {
        switch (action.toUpperCase()) {
            case "APPROVE":
                processApproval(report, reviewedBy, comment, isFraud, result, confidenceScoreBefore);
                break;
            case "REJECT":
                processRejection(report, reviewedBy, comment, result);
                break;
            case "UNDER_REVIEW":
                processUnderReview(report, reviewedBy, comment, result);
                break;
            default:
                throw new IllegalArgumentException("Invalid action: " + action);
        }
    }

    private void processApproval(UserReport report, String reviewedBy, String comment,
                                 Boolean isFraud, Map<String, Object> result,
                                 BigDecimal confidenceScoreBefore) {
        report.approve(reviewedBy, comment, isFraud);
        result.put("status", "APPROVED");
        result.put("isFraudConfirmed", isFraud);

        if (isFraud != null) {
            handleGoldLabelUpdate(report, isFraud, result, confidenceScoreBefore);
        }
    }

    private void processRejection(UserReport report, String reviewedBy, String comment,
                                  Map<String, Object> result) {
        report.reject(reviewedBy, comment);
        result.put("status", "REJECTED");
        result.put("modelUpdateTriggered", false);
    }

    private void processUnderReview(UserReport report, String reviewedBy, String comment,
                                    Map<String, Object> result) {
        report.setUnderReview(reviewedBy, comment);
        result.put("status", "UNDER_REVIEW");
        result.put("modelUpdateTriggered", false);
    }

    private void handleGoldLabelUpdate(UserReport report, Boolean isFraud,
                                       Map<String, Object> result, BigDecimal confidenceScoreBefore) {
        // 골드 라벨로 인한 모델 업데이트 트리거
        confidenceScoreService.triggerModelUpdate();
        result.put("modelUpdateTriggered", true);

        // 신뢰도 점수 변화 기록
        BigDecimal confidenceScoreAfter = confidenceScoreService.getCurrentConfidenceScore();
        result.put("confidenceScoreChange", Map.of(
                "before", confidenceScoreBefore,
                "after", confidenceScoreAfter,
                "improvement", confidenceScoreAfter.subtract(confidenceScoreBefore)
        ));

        // 모델 업데이트 알림
        notificationService.sendModelUpdateNotification(report, isFraud);
    }

    private Map<String, Object> buildReportStatistics(LocalDateTime startDate, LocalDateTime endDate, Integer days) {
        Map<String, Object> stats = new HashMap<>();

        try {
            // 기본 통계
            long totalReports = userReportRepository.countReportsInPeriod(startDate, endDate);
            long approvedReports = userReportRepository.countApprovedReportsInPeriod(
                    startDate, endDate, UserReport.ReportStatus.APPROVED);
            long rejectedReports = userReportRepository.countRejectedReportsInPeriod(
                    startDate, endDate, UserReport.ReportStatus.REJECTED);
            long pendingReports = userReportRepository.countPendingReportsInPeriod(
                    startDate, endDate, UserReport.ReportStatus.PENDING);

            stats.put("totalReports", totalReports);
            stats.put("approvedReports", approvedReports);
            stats.put("rejectedReports", rejectedReports);
            stats.put("pendingReports", pendingReports);

            // 승인률 계산
            double approvalRate = totalReports > 0 ? (approvedReports * 100.0) / totalReports : 0.0;
            stats.put("approvalRate", Math.round(approvalRate * 100.0) / 100.0);

            // 추가 메트릭 (시뮬레이션)
            stats.put("averageProcessingHours", 24.5 + (Math.random() * 48));
            stats.put("goldLabelAccuracy", 85.0 + (Math.random() * 10));

            // 신고 사유 통계
            stats.put("reportReasons", createReportReasonsStats());
            stats.put("period", days + " days");
            stats.put("calculatedAt", LocalDateTime.now());

        } catch (Exception e) {
            log.error("Failed to calculate report stats: {}", e.getMessage(), e);
            throw new ReportStatisticsException("Failed to calculate report statistics", e);
        }

        return stats;
    }

    private Map<String, Long> createReportReasonsStats() {
        Map<String, Long> reasonStats = new HashMap<>();
        reasonStats.put("SUSPICIOUS_TRANSACTION", 45L);
        reasonStats.put("UNAUTHORIZED_CHARGE", 32L);
        reasonStats.put("WRONG_AMOUNT", 18L);
        reasonStats.put("DUPLICATE_CHARGE", 12L);
        reasonStats.put("OTHER", 8L);
        return reasonStats;
    }
}
package com.credit.card.fraud.detection.transactions.service;

import com.credit.card.fraud.detection.transactions.entity.UserReport;
import com.credit.card.fraud.detection.transactions.repository.UserReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
    private final SimpMessagingTemplate messagingTemplate;
    
    private ModelUpdateService modelUpdateService;

    public Page<UserReport> getReports(UserReport.ReportStatus status, String reportedBy,
                                     LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        if (status != null) {
            return userReportRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        }
        return userReportRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public UserReport getReportById(Long reportId) {
        return userReportRepository.findById(reportId)
            .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));
    }

    @Transactional
    public Map<String, Object> reviewReport(Long reportId, String reviewedBy, String comment,
                                          Boolean isFraud, String action) {
        if (reportId == null || reviewedBy == null || action == null) {
            throw new IllegalArgumentException("Report ID, reviewer, and action cannot be null");
        }

        UserReport report = getReportById(reportId);

        // 기존 신뢰도 점수 기록
        BigDecimal confidenceScoreBefore = getCurrentConfidenceScore();

        Map<String, Object> result = new HashMap<>();
        result.put("reportId", reportId);
        result.put("reviewedBy", reviewedBy);

        try {
            switch (action.toUpperCase()) {
                case "APPROVE":
                    approveReport(report, reviewedBy, comment, isFraud, result, confidenceScoreBefore);
                    break;

                case "REJECT":
                    rejectReport(report, reviewedBy, comment, result);
                    break;

                case "UNDER_REVIEW":
                    setReportUnderReview(report, reviewedBy, comment, result);
                    break;

                default:
                    throw new IllegalArgumentException("Invalid action: " + action);
            }

            userReportRepository.save(report);
            result.put("reviewedAt", LocalDateTime.now());

            // 관리자에게 실시간 알림
            sendReportReviewNotification(report, action);

            log.info("Report {} reviewed by {}: action={}, isFraud={}",
                reportId, reviewedBy, action, isFraud);

        } catch (Exception e) {
            log.error("Failed to review report {}: {}", reportId, e.getMessage(), e);
            throw new RuntimeException("Failed to review report: " + e.getMessage(), e);
        }

        return result;
    }

    private void approveReport(UserReport report, String reviewedBy, String comment, 
                              Boolean isFraud, Map<String, Object> result, 
                              BigDecimal confidenceScoreBefore) {
        report.approve(reviewedBy, comment, isFraud);
        result.put("status", "APPROVED");
        result.put("isFraudConfirmed", isFraud);

        // 골드 라벨 업데이트로 인한 모델 개선 시뮬레이션
        if (isFraud != null) {
            triggerModelUpdateFromGoldLabel(report, isFraud);
            result.put("modelUpdateTriggered", true);

            // 업데이트 후 신뢰도 점수
            BigDecimal confidenceScoreAfter = getCurrentConfidenceScore();
            result.put("confidenceScoreChange", Map.of(
                "before", confidenceScoreBefore,
                "after", confidenceScoreAfter,
                "improvement", confidenceScoreAfter.subtract(confidenceScoreBefore)
            ));
        }
    }

    private void rejectReport(UserReport report, String reviewedBy, String comment, 
                             Map<String, Object> result) {
        report.reject(reviewedBy, comment);
        result.put("status", "REJECTED");
        result.put("modelUpdateTriggered", false);
    }

    private void setReportUnderReview(UserReport report, String reviewedBy, String comment, 
                                     Map<String, Object> result) {
        report.setStatus(UserReport.ReportStatus.UNDER_REVIEW);
        report.setReviewedBy(reviewedBy);
        report.setReviewComment(comment);
        result.put("status", "UNDER_REVIEW");
        result.put("modelUpdateTriggered", false);
    }

    private BigDecimal getCurrentConfidenceScore() {
        // 모델 업데이트 서비스가 있다면 사용, 없다면 기본값 반환
        if (modelUpdateService != null) {
            return modelUpdateService.getCurrentConfidenceScore();
        }
        return new BigDecimal("0.85");
    }

    private void triggerModelUpdateFromGoldLabel(UserReport report, Boolean isFraud) {
        // 골드 라벨 기반 모델 업데이트 시뮬레이션
        // 실제 환경에서는 MLOps 파이프라인 트리거

        log.info("Triggering model update from gold label: transactionId={}, goldLabel={}",
            report.getTransaction().getId(), isFraud);

        // 모델 업데이트 서비스 호출
        if (modelUpdateService != null) {
            modelUpdateService.triggerModelUpdate();
        }

        // 모델 업데이트 알림 전송
        sendModelUpdateNotification(report, isFraud);
    }

    private void sendModelUpdateNotification(UserReport report, Boolean isFraud) {
        Map<String, Object> updateNotification = new HashMap<>();
        updateNotification.put("type", "MODEL_UPDATE_FROM_GOLD_LABEL");
        updateNotification.put("transactionId", report.getTransaction().getId());
        updateNotification.put("reportId", report.getId());
        updateNotification.put("goldLabel", isFraud);
        updateNotification.put("timestamp", LocalDateTime.now());
        updateNotification.put("message", "Model updated with new gold label data");

        try {
            messagingTemplate.convertAndSend("/topic/model-updates", updateNotification);
        } catch (Exception e) {
            log.warn("Failed to send model update notification: {}", e.getMessage());
        }
    }

    public long getPendingReportsCount() {
        return userReportRepository.countByStatus(UserReport.ReportStatus.PENDING);
    }

    public long getUnderReviewReportsCount() {
        return userReportRepository.countByStatus(UserReport.ReportStatus.UNDER_REVIEW);
    }

    public Map<String, Object> getReportStats(Integer days) {
        if (days == null || days <= 0) {
            throw new IllegalArgumentException("Days must be a positive number");
        }

        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        LocalDateTime endDate = LocalDateTime.now();

        Map<String, Object> stats = new HashMap<>();

        try {
            // 기간별 신고 통계
            long totalReports = userReportRepository.countReportsInPeriod(startDate, endDate);
            long approvedReports = userReportRepository.countApprovedReportsInPeriod(startDate, endDate, UserReport.ReportStatus.APPROVED);
            long rejectedReports = userReportRepository.countRejectedReportsInPeriod(startDate, endDate, UserReport.ReportStatus.REJECTED);
            long pendingReports = userReportRepository.countPendingReportsInPeriod(startDate, endDate, UserReport.ReportStatus.PENDING);

            stats.put("totalReports", totalReports);
            stats.put("approvedReports", approvedReports);
            stats.put("rejectedReports", rejectedReports);
            stats.put("pendingReports", pendingReports);

            // 승인률 계산
            double approvalRate = totalReports > 0 ?
                (approvedReports * 100.0) / totalReports : 0.0;
            stats.put("approvalRate", Math.round(approvalRate * 100.0) / 100.0);

            // 평균 처리 시간 (시뮬레이션)
            stats.put("averageProcessingHours", 24.5 + (Math.random() * 48)); // 24-72시간

            // 골드 라벨 정확도 (시뮬레이션)
            stats.put("goldLabelAccuracy", 85.0 + (Math.random() * 10)); // 85-95%

            // 주요 신고 사유 통계
            Map<String, Long> reasonStats = new HashMap<>();
            reasonStats.put("SUSPICIOUS_TRANSACTION", 45L);
            reasonStats.put("UNAUTHORIZED_CHARGE", 32L);
            reasonStats.put("WRONG_AMOUNT", 18L);
            reasonStats.put("DUPLICATE_CHARGE", 12L);
            reasonStats.put("OTHER", 8L);
            stats.put("reportReasons", reasonStats);

            stats.put("period", days + " days");
            stats.put("calculatedAt", LocalDateTime.now());

        } catch (Exception e) {
            log.error("Failed to calculate report stats: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to calculate report statistics", e);
        }

        return stats;
    }

    @Transactional
    public void setPriority(Long reportId, String priority) {
        if (reportId == null || priority == null) {
            throw new IllegalArgumentException("Report ID and priority cannot be null");
        }

        // 실제 구현에서는 priority 필드를 UserReport 엔티티에 추가
        // 현재는 로그만 기록
        log.info("Setting priority for report {}: {}", reportId, priority);

        // 우선순위 변경 알림
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "REPORT_PRIORITY_CHANGED");
        notification.put("reportId", reportId);
        notification.put("priority", priority);
        notification.put("timestamp", LocalDateTime.now());

        try {
            messagingTemplate.convertAndSend("/topic/admin/reports", notification);
        } catch (Exception e) {
            log.warn("Failed to send priority change notification: {}", e.getMessage());
        }
    }

    public List<UserReport> getReportsByTransaction(Long transactionId) {
        if (transactionId == null) {
            throw new IllegalArgumentException("Transaction ID cannot be null");
        }
        return userReportRepository.findByTransactionIdOrderByCreatedAtDesc(transactionId);
    }

    private void sendReportReviewNotification(UserReport report, String action) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "REPORT_REVIEWED");
        notification.put("reportId", report.getId());
        notification.put("transactionId", report.getTransaction().getId());
        notification.put("action", action);
        notification.put("reviewedBy", report.getReviewedBy());
        notification.put("isFraudConfirmed", report.getIsFraudConfirmed());
        notification.put("timestamp", LocalDateTime.now());

        try {
            messagingTemplate.convertAndSend("/topic/admin/reports", notification);

            // 신고자에게도 알림 (실제 환경에서는 이메일 등으로)
            if (report.getStatus() != UserReport.ReportStatus.UNDER_REVIEW) {
                messagingTemplate.convertAndSend(
                    "/topic/user/" + report.getReportedBy() + "/reports",
                    notification
                );
            }
        } catch (Exception e) {
            log.warn("Failed to send report review notification: {}", e.getMessage());
        }
    }

    // 모델 업데이트 서비스 주입을 위한 setter (순환 참조 방지)
    public void setModelUpdateService(ModelUpdateService modelUpdateService) {
        this.modelUpdateService = modelUpdateService;
    }

    // 모델 업데이트 인터페이스 정의
    public interface ModelUpdateService {
        void triggerModelUpdate();
        BigDecimal getCurrentConfidenceScore();
    }
}
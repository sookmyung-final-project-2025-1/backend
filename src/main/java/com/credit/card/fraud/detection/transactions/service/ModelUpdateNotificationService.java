package com.credit.card.fraud.detection.transactions.service;

import com.credit.card.fraud.detection.transactions.entity.UserReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModelUpdateNotificationService {

    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

    /**
     * 새로운 신고 접수 알림 (관리자용)
     */
    public void sendReportNotification(UserReport report) {
        if (messagingTemplate == null) {
            log.debug("WebSocket messaging not available, skipping report notification");
            return;
        }

        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "NEW_FRAUD_REPORT");
            notification.put("reportId", report.getId());
            notification.put("transactionId", report.getTransaction().getId());
            notification.put("reportedBy", report.getReportedBy());
            notification.put("reason", report.getReason());
            notification.put("description", report.getDescription());
            notification.put("transactionAmount", report.getTransaction().getAmount());
            notification.put("merchant", report.getTransaction().getMerchant());
            notification.put("timestamp", LocalDateTime.now());
            notification.put("priority", determinePriority(report));

            // 관리자 채널로 알림 전송
            messagingTemplate.convertAndSend("/topic/admin/reports", notification);

            // 대시보드 실시간 업데이트용
            messagingTemplate.convertAndSend("/topic/admin/dashboard", Map.of(
                    "type", "REPORT_COUNT_UPDATE",
                    "action", "INCREMENT",
                    "timestamp", LocalDateTime.now()
            ));

            log.info("Report notification sent for report {} (transaction: {})",
                    report.getId(), report.getTransaction().getId());

        } catch (Exception e) {
            log.warn("Failed to send report notification for report {}: {}",
                    report.getId(), e.getMessage());
        }
    }

    /**
     * 신고 검토 완료 알림
     */
    public void sendReportReviewNotification(UserReport report, String action) {
        if (messagingTemplate == null) {
            log.debug("WebSocket messaging not available, skipping report review notification");
            return;
        }

        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "REPORT_REVIEWED");
            notification.put("reportId", report.getId());
            notification.put("transactionId", report.getTransaction().getId());
            notification.put("action", action);
            notification.put("reviewedBy", Optional.ofNullable(report.getReviewedBy()).orElse(""));
            notification.put("isFraudConfirmed", report.getIsFraudConfirmed());
            notification.put("status", report.getStatus().name());
            notification.put("timestamp", LocalDateTime.now());

            // 관리자에게 알림
            messagingTemplate.convertAndSend("/topic/admin/reports", notification);

            // 신고자에게 알림 (완료된 검토만)
            if (report.isReviewed()) {
                Map<String, Object> userNotification = new HashMap<>();
                userNotification.put("type", "YOUR_REPORT_REVIEWED");
                userNotification.put("reportId", report.getId());
                userNotification.put("transactionId", report.getTransaction().getId());
                userNotification.put("status", report.getStatus().name());
                userNotification.put("message", createUserMessage(report));
                userNotification.put("timestamp", LocalDateTime.now());

                messagingTemplate.convertAndSend(
                        "/topic/user/" + report.getReportedBy() + "/reports",
                        userNotification
                );
            }

            log.debug("Report review notification sent for report {}", report.getId());

        } catch (Exception e) {
            log.warn("Failed to send report review notification for report {}: {}",
                    report.getId(), e.getMessage());
        }
    }

    /**
     * 모델 업데이트 알림 (골드 라벨 적용 시)
     */
    public void sendModelUpdateNotification(UserReport report, Boolean isFraud) {
        if (messagingTemplate == null) {
            log.debug("WebSocket messaging not available, skipping model update notification");
            return;
        }

        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "MODEL_UPDATE_FROM_GOLD_LABEL");
            notification.put("transactionId", report.getTransaction().getId());
            notification.put("reportId", report.getId());
            notification.put("goldLabel", isFraud);
            notification.put("labelType", isFraud ? "FRAUD" : "LEGITIMATE");
            notification.put("reviewedBy", report.getReviewedBy());
            notification.put("timestamp", LocalDateTime.now());
            notification.put("message", "Model updated with new gold label data");

            // 모델 업데이트 채널로 알림
            messagingTemplate.convertAndSend("/topic/model-updates", notification);

            // 관리자 대시보드에도 알림
            messagingTemplate.convertAndSend("/topic/admin/model", Map.of(
                    "type", "GOLD_LABEL_APPLIED",
                    "isFraud", isFraud,
                    "timestamp", LocalDateTime.now()
            ));

            log.info("Model update notification sent for transaction {} with gold label: {}",
                    report.getTransaction().getId(), isFraud);

        } catch (Exception e) {
            log.warn("Failed to send model update notification for transaction {}: {}",
                    report.getTransaction().getId(), e.getMessage());
        }
    }

    /**
     * 신고 우선순위 변경 알림
     */
    public void sendPriorityChangeNotification(Long reportId, String priority) {
        if (messagingTemplate == null) {
            log.debug("WebSocket messaging not available, skipping priority change notification");
            return;
        }

        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "REPORT_PRIORITY_CHANGED");
            notification.put("reportId", reportId);
            notification.put("priority", priority);
            notification.put("timestamp", LocalDateTime.now());
            notification.put("message", "Report priority updated to " + priority);

            messagingTemplate.convertAndSend("/topic/admin/reports", notification);

            log.debug("Priority change notification sent for report {}: {}", reportId, priority);

        } catch (Exception e) {
            log.warn("Failed to send priority change notification for report {}: {}",
                    reportId, e.getMessage());
        }
    }

    /**
     * 시스템 알림 (일반적인 시스템 메시지)
     */
    public void sendSystemNotification(String type, String message, Map<String, Object> additionalData) {
        if (messagingTemplate == null) {
            log.debug("WebSocket messaging not available, skipping system notification");
            return;
        }

        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", type);
            notification.put("message", message);
            notification.put("timestamp", LocalDateTime.now());

            if (additionalData != null) {
                notification.putAll(additionalData);
            }

            messagingTemplate.convertAndSend("/topic/system", notification);

            log.debug("System notification sent: type={}, message={}", type, message);

        } catch (Exception e) {
            log.warn("Failed to send system notification: {}", e.getMessage());
        }
    }

    /**
     * 대량 처리 완료 알림
     */
    public void sendBatchProcessingNotification(String operation, int totalCount, int successCount, int failCount) {
        if (messagingTemplate == null) {
            return;
        }

        try {
            Map<String, Object> notification = Map.of(
                    "type", "BATCH_PROCESSING_COMPLETED",
                    "operation", operation,
                    "totalCount", totalCount,
                    "successCount", successCount,
                    "failCount", failCount,
                    "successRate", totalCount > 0 ? (successCount * 100.0 / totalCount) : 0.0,
                    "timestamp", LocalDateTime.now()
            );

            messagingTemplate.convertAndSend("/topic/admin/batch", notification);

            log.info("Batch processing notification sent: operation={}, success={}/{}",
                    operation, successCount, totalCount);

        } catch (Exception e) {
            log.warn("Failed to send batch processing notification: {}", e.getMessage());
        }
    }

    // Private helper methods

    private String determinePriority(UserReport report) {
        // 거래 금액과 사유에 따른 우선순위 결정
        if (report.getTransaction().getAmount().doubleValue() > 1000000) { // 100만원 이상
            return "HIGH";
        }

        if (report.getReason().contains("UNAUTHORIZED") || report.getReason().contains("FRAUD")) {
            return "HIGH";
        }

        if (report.getReason().contains("SUSPICIOUS")) {
            return "MEDIUM";
        }

        return "LOW";
    }

    private String createUserMessage(UserReport report) {
        switch (report.getStatus()) {
            case APPROVED:
                if (Boolean.TRUE.equals(report.getIsFraudConfirmed())) {
                    return "신고해주신 거래가 사기로 확인되었습니다. 신속한 신고 감사합니다.";
                } else {
                    return "신고해주신 거래가 정상 거래로 확인되었습니다.";
                }
            case REJECTED:
                return "신고해주신 내용을 검토한 결과, 추가 조치가 필요하지 않은 것으로 판단됩니다.";
            default:
                return "신고가 처리되었습니다.";
        }
    }
}
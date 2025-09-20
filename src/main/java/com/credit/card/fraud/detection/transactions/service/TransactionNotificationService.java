package com.credit.card.fraud.detection.transactions.service;

import com.credit.card.fraud.detection.transactions.entity.FraudDetectionResult;
import com.credit.card.fraud.detection.transactions.entity.Transaction;
import com.credit.card.fraud.detection.transactions.entity.UserReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionNotificationService {

    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

    public void handleDetectionResult(Transaction transaction, FraudDetectionResult detectionResult) {
        // 고위험 거래 알림
        if (isHighRisk(detectionResult)) {
            sendHighRiskAlert(transaction, detectionResult);
        }

        // 실시간 거래 업데이트
        sendRealtimeUpdate(transaction, detectionResult);
    }

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
            notification.put("timestamp", LocalDateTime.now());

            messagingTemplate.convertAndSend("/topic/admin/reports", notification);
            log.debug("Report notification sent for report {}", report.getId());
        } catch (Exception e) {
            log.warn("Failed to send report notification for report {}: {}",
                    report.getId(), e.getMessage());
        }
    }

    private boolean isHighRisk(FraudDetectionResult detectionResult) {
        return detectionResult.getFinalScore().compareTo(new BigDecimal("0.8")) > 0;
    }

    private void sendHighRiskAlert(Transaction transaction, FraudDetectionResult detectionResult) {
        if (messagingTemplate == null) {
            log.debug("WebSocket messaging not available, skipping high risk alert");
            return;
        }

        try {
            Map<String, Object> alert = new HashMap<>();
            alert.put("type", "HIGH_RISK_TRANSACTION");
            alert.put("transactionId", transaction.getId());
            alert.put("userId", transaction.getUserId());
            alert.put("amount", transaction.getAmount());
            alert.put("merchant", transaction.getMerchant());
            alert.put("fraudScore", detectionResult.getFinalScore());
            alert.put("riskLevel", detectionResult.getRiskLevel());
            alert.put("timestamp", LocalDateTime.now());

            messagingTemplate.convertAndSend("/topic/alerts", alert);
            log.debug("High risk alert sent for transaction {}", transaction.getId());
        } catch (Exception e) {
            log.warn("Failed to send high risk alert for transaction {}: {}",
                    transaction.getId(), e.getMessage());
        }
    }

    private void sendRealtimeUpdate(Transaction transaction, FraudDetectionResult detectionResult) {
        if (messagingTemplate == null) {
            log.debug("WebSocket messaging not available, skipping realtime update");
            return;
        }

        try {
            Map<String, Object> update = new HashMap<>();
            update.put("type", "TRANSACTION_PROCESSED");
            update.put("transactionId", transaction.getId());
            update.put("finalScore", detectionResult.getFinalScore());
            update.put("prediction", detectionResult.getFinalPrediction());
            update.put("riskLevel", detectionResult.getRiskLevel());
            update.put("timestamp", LocalDateTime.now());

            messagingTemplate.convertAndSend("/topic/transactions", update);
            log.debug("Realtime update sent for transaction {}", transaction.getId());
        } catch (Exception e) {
            log.warn("Failed to send realtime update for transaction {}: {}",
                    transaction.getId(), e.getMessage());
        }
    }
}
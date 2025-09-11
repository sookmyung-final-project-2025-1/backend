package com.credit.card.fraud.detection.transactions.controller;

import com.credit.card.fraud.detection.transactions.entity.FraudDetectionResult;
import com.credit.card.fraud.detection.transactions.entity.Transaction;
import com.credit.card.fraud.detection.transactions.entity.UserReport;
import com.credit.card.fraud.detection.transactions.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Tag(name = "거래 관리", description = "거래 조회 및 사기 탐지 API")
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping
    @Operation(summary = "거래 목록 조회", description = "다양한 필터 조건을 사용하여 거래 목록을 조회합니다")
    public ResponseEntity<List<Transaction>> getTransactions(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String merchant,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) Boolean isFraud,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @PageableDefault(size = 50) Pageable pageable) {

        if (startTime == null) {
            startTime = LocalDateTime.now().minusDays(7);
        }
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }

        List<Transaction> transactions = transactionService.getTransactionsWithFilters(
            userId, merchant, category, minAmount, maxAmount, isFraud,
            startTime, endTime, pageable);

        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/{transactionId}")
    @Operation(summary = "거래 상세 조회", description = "거래 ID로 특정 거래의 상세 정보를 조회합니다")
    public ResponseEntity<Transaction> getTransaction(@PathVariable Long transactionId) {
        // 실제 구현에서는 TransactionRepository에서 직접 조회
        // 여기서는 간소화를 위해 생략
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/{transactionId}/fraud-detection")
    @Operation(summary = "사기 탐지 결과 조회", description = "특정 거래의 사기 탐지 결과를 조회합니다")
    public ResponseEntity<FraudDetectionResult> getFraudDetectionResult(@PathVariable Long transactionId) {
        Optional<FraudDetectionResult> result = transactionService.getFraudDetectionResult(transactionId);
        return result.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{transactionId}/report")
    @Operation(summary = "사기 신고", description = "특정 거래에 대한 사기 신고를 제출합니다")
    public ResponseEntity<UserReport> reportFraud(
            @PathVariable Long transactionId,
            @RequestBody UserReportRequest request) {

        UserReport report = transactionService.reportFraud(
            transactionId, 
            request.getReportedBy(), 
            request.getReason(), 
            request.getDescription()
        );

        return ResponseEntity.ok(report);
    }

    @PutMapping("/reports/{reportId}/approve")
    @Operation(summary = "사기 신고 승인", description = "사기 신고를 승인하거나 거부합니다 (관리자 전용)")
    public ResponseEntity<Void> approveReport(
            @PathVariable Long reportId,
            @RequestBody ReportReviewRequest request) {

        transactionService.approveReport(
            reportId, 
            request.getReviewedBy(), 
            request.getComment(), 
            request.getIsFraud()
        );

        return ResponseEntity.ok().build();
    }

    // DTO classes
    public static class UserReportRequest {
        private String reportedBy;
        private String reason;
        private String description;

        // getters and setters
        public String getReportedBy() { return reportedBy; }
        public void setReportedBy(String reportedBy) { this.reportedBy = reportedBy; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    public static class ReportReviewRequest {
        private String reviewedBy;
        private String comment;
        private Boolean isFraud;

        // getters and setters
        public String getReviewedBy() { return reviewedBy; }
        public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
        public Boolean getIsFraud() { return isFraud; }
        public void setIsFraud(Boolean isFraud) { this.isFraud = isFraud; }
    }
}
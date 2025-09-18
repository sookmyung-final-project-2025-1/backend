package com.credit.card.fraud.detection.transactions.controller;

import com.credit.card.fraud.detection.transactions.dto.UserReportRequest;
import com.credit.card.fraud.detection.transactions.dto.UserReportResponse;
import com.credit.card.fraud.detection.transactions.dto.ReportReviewRequest;
import com.credit.card.fraud.detection.transactions.entity.UserReport;
import com.credit.card.fraud.detection.transactions.service.TransactionService;
import com.credit.card.fraud.detection.transactions.service.UserReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "사용자 신고 관리", description = "사기 거래 신고 및 골드 라벨 관리 API")
public class UserReportController {

    private final UserReportService userReportService;
    private final TransactionService transactionService;

    @PostMapping
    @Operation(summary = "사기 거래 신고", description = "사용자가 의심스러운 거래를 신고합니다")
    @ApiResponse(responseCode = "200", description = "신고 접수 성공",
        content = @Content(mediaType = "application/json",
            examples = @ExampleObject(value = """
                {
                  "reportId": 12345,
                  "transactionId": 67890,
                  "reportedBy": "user@example.com",
                  "reason": "SUSPICIOUS_TRANSACTION",
                  "description": "거래 시간과 위치가 이상합니다",
                  "status": "PENDING",
                  "reviewedBy": null,
                  "reviewComment": null,
                  "isFraudConfirmed": false,
                  "reportedAt": "2025-09-18T14:30:00",
                  "reviewedAt": null,
                  "transactionDetails": {
                    "amount": 150000.00,
                    "merchant": "WorldWide_Store_123",
                    "userId": "USER_12345",
                    "transactionTime": "2025-09-18T14:25:00"
                  },
                  "priority": null,
                  "severity": null,
                  "category": null,
                  "message": "신고가 성공적으로 접수되었습니다"
                }""")))
    public ResponseEntity<UserReportResponse> reportTransaction(@Valid @RequestBody UserReportRequest request) {
        UserReport report = transactionService.reportFraud(
            request.getTransactionId(),
            request.getReportedBy(),
            request.getReason(),
            request.getDescription()
        );

        UserReportResponse response = UserReportResponse.builder()
            .reportId(report.getId())
            .transactionId(report.getTransaction().getId())
            .reportedBy(report.getReportedBy())
            .reason(report.getReason())
            .description(report.getDescription())
            .status(report.getStatus().name())
            .reviewedBy(report.getReviewedBy())
            .reviewComment(report.getReviewComment())
            .isFraudConfirmed(report.getIsFraudConfirmed())
            .reportedAt(report.getCreatedAt())
            .reviewedAt(report.getUpdatedAt())
            .transactionDetails(Map.of(
                "amount", report.getTransaction().getAmount(),
                "merchant", report.getTransaction().getMerchant(),
                "userId", report.getTransaction().getUserId(),
                "transactionTime", report.getTransaction().getTransactionTime()
            ))
            .message("신고가 성공적으로 접수되었습니다")
            .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "신고 목록 조회", description = "모든 신고 내역을 조회합니다 (관리자 전용)")
    public ResponseEntity<Page<UserReportResponse>> getReports(
            @RequestParam(required = false) UserReport.ReportStatus status,
            @RequestParam(required = false) String reportedBy,
            @RequestParam(required = false) LocalDateTime startDate,
            @RequestParam(required = false) LocalDateTime endDate,
            Pageable pageable) {

        Page<UserReport> reports = userReportService.getReports(status, reportedBy, startDate, endDate, pageable);
        Page<UserReportResponse> response = reports.map(this::mapToResponse);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{reportId}")
    @Operation(summary = "신고 상세 조회", description = "특정 신고의 상세 정보를 조회합니다")
    public ResponseEntity<UserReportResponse> getReport(@PathVariable Long reportId) {
        UserReport report = userReportService.getReportById(reportId);
        return ResponseEntity.ok(mapToResponse(report));
    }

    @PutMapping("/{reportId}/review")
    @Operation(summary = "신고 검토 및 승인/거부", description = "관리자가 신고를 검토하고 골드 라벨을 적용합니다")
    @ApiResponse(responseCode = "200", description = "검토 완료",
        content = @Content(mediaType = "application/json",
            examples = @ExampleObject(value = """
                {
                  "reportId": 12345,
                  "status": "APPROVED",
                  "reviewedBy": "admin@company.com",
                  "isFraudConfirmed": true,
                  "modelUpdateTriggered": true,
                  "confidenceScoreChange": {
                    "before": 0.823,
                    "after": 0.857,
                    "improvement": 0.034
                  }
                }""")))
    public ResponseEntity<Map<String, Object>> reviewReport(
            @PathVariable Long reportId,
            @Valid @RequestBody ReportReviewRequest request) {

        Map<String, Object> result = userReportService.reviewReport(
            reportId,
            request.getReviewedBy(),
            request.getComment(),
            request.getIsFraud(),
            request.getAction()
        );

        return ResponseEntity.ok(result);
    }

    @GetMapping("/pending/count")
    @Operation(summary = "대기 중인 신고 수", description = "검토 대기 중인 신고의 수를 조회합니다")
    public ResponseEntity<Map<String, Object>> getPendingReportsCount() {
        long pendingCount = userReportService.getPendingReportsCount();
        long underReviewCount = userReportService.getUnderReviewReportsCount();

        return ResponseEntity.ok(Map.of(
            "pendingCount", pendingCount,
            "underReviewCount", underReviewCount,
            "totalAwaitingAction", pendingCount + underReviewCount
        ));
    }

    @GetMapping("/stats")
    @Operation(summary = "신고 통계", description = "신고 관련 통계를 조회합니다")
    public ResponseEntity<Map<String, Object>> getReportStats(
            @RequestParam(defaultValue = "7") Integer days) {

        Map<String, Object> stats = userReportService.getReportStats(days);
        return ResponseEntity.ok(stats);
    }

    @PostMapping("/{reportId}/priority")
    @Operation(summary = "신고 우선순위 설정", description = "신고의 우선순위를 변경합니다")
    public ResponseEntity<Map<String, Object>> setPriority(
            @PathVariable Long reportId,
            @RequestParam String priority) {

        userReportService.setPriority(reportId, priority);

        return ResponseEntity.ok(Map.of(
            "reportId", reportId,
            "priority", priority,
            "updatedAt", LocalDateTime.now()
        ));
    }

    @GetMapping("/transaction/{transactionId}")
    @Operation(summary = "특정 거래의 신고 내역", description = "특정 거래에 대한 모든 신고 내역을 조회합니다")
    public ResponseEntity<List<UserReportResponse>> getReportsByTransaction(@PathVariable Long transactionId) {
        List<UserReport> reports = userReportService.getReportsByTransaction(transactionId);
        List<UserReportResponse> response = reports.stream()
            .map(this::mapToResponse)
            .toList();

        return ResponseEntity.ok(response);
    }

    // Helper method
    private UserReportResponse mapToResponse(UserReport report) {
        return UserReportResponse.builder()
            .reportId(report.getId())
            .transactionId(report.getTransaction().getId())
            .reportedBy(report.getReportedBy())
            .reason(report.getReason())
            .description(report.getDescription())
            .status(report.getStatus().name())
            .reviewedBy(report.getReviewedBy())
            .reviewComment(report.getReviewComment())
            .isFraudConfirmed(report.getIsFraudConfirmed())
            .reportedAt(report.getCreatedAt())
            .reviewedAt(report.getUpdatedAt())
            .transactionDetails(Map.of(
                "amount", report.getTransaction().getAmount(),
                "merchant", report.getTransaction().getMerchant(),
                "userId", report.getTransaction().getUserId(),
                "transactionTime", report.getTransaction().getTransactionTime()
            ))
            .build();
    }
}
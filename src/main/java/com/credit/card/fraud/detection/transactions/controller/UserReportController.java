package com.credit.card.fraud.detection.transactions.controller;

import com.credit.card.fraud.detection.transactions.dto.UserReportRequest;
import com.credit.card.fraud.detection.transactions.dto.UserReportResponse;
import com.credit.card.fraud.detection.transactions.dto.ReportReviewRequest;
import com.credit.card.fraud.detection.transactions.entity.UserReport;
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
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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

    @PostMapping
    @Operation(summary = "사기 거래 신고", description = "사용자가 의심스러운 거래를 신고합니다")
    @ApiResponse(responseCode = "200", description = "신고 접수 성공",
            content = @Content(examples = @ExampleObject(value = """
            {
              "reportId": 123,
              "transactionId": 456,
              "reportedBy": "USER_12345",
              "reason": "SUSPICIOUS_TRANSACTION",
              "description": "거래 금액이 평소보다 비정상적으로 큼",
              "status": "PENDING",
              "reportedAt": "2024-01-15T14:30:00",
              "message": "신고가 성공적으로 접수되었습니다"
            }""")))
    @ApiResponse(responseCode = "400", description = "잘못된 요청")
    @ApiResponse(responseCode = "404", description = "거래를 찾을 수 없음")
    public ResponseEntity<UserReportResponse> createReport(@Valid @RequestBody UserReportRequest request) {
        try {
            UserReport report = userReportService.createReport(
                    request.getTransactionId(),
                    request.getReportedBy(),
                    request.getReason(),
                    request.getDescription()
            );

            UserReportResponse response = mapToResponse(report);
            response.setMessage("신고가 성공적으로 접수되었습니다");

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping
    @Operation(summary = "신고 목록 조회", description = "필터 조건에 따라 신고 내역을 조회합니다 (관리자 전용)")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    public ResponseEntity<Page<UserReportResponse>> getReports(
            @RequestParam(required = false) UserReport.ReportStatus status,
            @RequestParam(required = false) String reportedBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        Page<UserReport> reports = userReportService.getReports(status, reportedBy, startDate, endDate, pageable);
        Page<UserReportResponse> response = reports.map(this::mapToResponse);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{reportId}")
    @Operation(summary = "신고 상세 조회", description = "특정 신고의 상세 정보를 조회합니다")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "신고를 찾을 수 없음")
    public ResponseEntity<UserReportResponse> getReport(@PathVariable Long reportId) {
        try {
            UserReport report = userReportService.getReportById(reportId);
            return ResponseEntity.ok(mapToResponse(report));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{reportId}")
    @Operation(summary = "신고 검토", description = "관리자가 신고를 검토하고 골드 라벨을 적용합니다")
    @ApiResponse(responseCode = "200", description = "검토 완료",
            content = @Content(examples = @ExampleObject(value = """
            {
              "reportId": 123,
              "reviewedBy": "ADMIN_001",
              "reviewedAt": "2024-01-15T15:30:00",
              "status": "APPROVED",
              "isFraudConfirmed": true,
              "modelUpdateTriggered": true,
              "confidenceScoreChange": {
                "before": 0.85,
                "after": 0.87,
                "improvement": 0.02
              }
            }""")))
    @ApiResponse(responseCode = "400", description = "잘못된 요청")
    @ApiResponse(responseCode = "404", description = "신고를 찾을 수 없음")
    public ResponseEntity<Map<String, Object>> reviewReport(
            @PathVariable Long reportId,
            @Valid @RequestBody ReportReviewRequest request) {

        try {
            Map<String, Object> result = userReportService.reviewReport(
                    reportId,
                    request.getReviewedBy(),
                    request.getComment(),
                    request.getIsFraud(),
                    request.getAction()
            );

            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/stats")
    @Operation(summary = "신고 통계", description = "신고 관련 통계를 조회합니다")
    @ApiResponse(responseCode = "200", description = "통계 조회 성공",
            content = @Content(examples = @ExampleObject(value = """
            {
              "totalReports": 150,
              "approvedReports": 89,
              "rejectedReports": 45,
              "pendingReports": 16,
              "approvalRate": 59.33,
              "averageProcessingHours": 36.2,
              "goldLabelAccuracy": 92.5,
              "reportReasons": {
                "SUSPICIOUS_TRANSACTION": 45,
                "UNAUTHORIZED_CHARGE": 32,
                "WRONG_AMOUNT": 18,
                "DUPLICATE_CHARGE": 12,
                "OTHER": 8
              },
              "period": "7 days",
              "calculatedAt": "2024-01-15T14:30:00"
            }""")))
    public ResponseEntity<Map<String, Object>> getStats(
            @RequestParam(defaultValue = "7") Integer days) {

        if (days == null || days <= 0) {
            return ResponseEntity.badRequest().build();
        }

        Map<String, Object> stats = userReportService.getReportStats(days);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/pending/count")
    @Operation(summary = "대기 중인 신고 수", description = "검토 대기 중인 신고의 수를 조회합니다")
    @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(examples = @ExampleObject(value = """
            {
              "pendingCount": 16,
              "underReviewCount": 8,
              "totalUnprocessed": 24,
              "timestamp": "2024-01-15T14:30:00"
            }""")))
    public ResponseEntity<Map<String, Object>> getPendingCount() {
        long pendingCount = userReportService.getPendingReportsCount();
        long underReviewCount = userReportService.getUnderReviewReportsCount();

        return ResponseEntity.ok(Map.of(
                "pendingCount", pendingCount,
                "underReviewCount", underReviewCount,
                "totalUnprocessed", pendingCount + underReviewCount,
                "timestamp", LocalDateTime.now()
        ));
    }

    @GetMapping("/transactions/{transactionId}")
    @Operation(summary = "거래별 신고 내역", description = "특정 거래에 대한 모든 신고 내역을 조회합니다")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    public ResponseEntity<List<UserReportResponse>> getReportsByTransaction(@PathVariable Long transactionId) {
        List<UserReport> reports = userReportService.getReportsByTransaction(transactionId);
        List<UserReportResponse> response = reports.stream()
                .map(this::mapToResponse)
                .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/reporter/{reportedBy}")
    @Operation(summary = "신고자별 신고 내역", description = "특정 신고자의 신고 내역을 조회합니다")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    public ResponseEntity<List<UserReportResponse>> getReportsByReporter(
            @PathVariable String reportedBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        List<UserReport> reports = userReportService.getReportsByReporter(reportedBy, startDate, endDate);
        List<UserReportResponse> response = reports.stream()
                .map(this::mapToResponse)
                .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/recent")
    @Operation(summary = "최근 신고 활동", description = "최근 시간 내의 신고 활동을 조회합니다")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    public ResponseEntity<List<UserReportResponse>> getRecentActivity(
            @RequestParam(defaultValue = "24") Integer hours) {

        if (hours == null || hours <= 0) {
            hours = 24;
        }

        List<UserReport> reports = userReportService.getRecentActivity(hours);
        List<UserReportResponse> response = reports.stream()
                .map(this::mapToResponse)
                .toList();

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{reportId}/priority")
    @Operation(summary = "우선순위 설정", description = "신고의 우선순위를 변경합니다")
    @ApiResponse(responseCode = "200", description = "우선순위 변경 성공")
    @ApiResponse(responseCode = "400", description = "잘못된 요청")
    @ApiResponse(responseCode = "404", description = "신고를 찾을 수 없음")
    public ResponseEntity<Map<String, Object>> setPriority(
            @PathVariable Long reportId,
            @RequestParam String priority) {

        try {
            userReportService.setPriority(reportId, priority);
            return ResponseEntity.ok(Map.of(
                    "reportId", reportId,
                    "priority", priority,
                    "updatedAt", LocalDateTime.now(),
                    "message", "우선순위가 성공적으로 변경되었습니다"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

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
                        "transactionTime", report.getTransaction().getTransactionTime(),
                        "currency", report.getTransaction().getCurrency() != null ?
                                report.getTransaction().getCurrency() : "KRW"
                ))
                .build();
    }
}
package com.credit.card.fraud.detection.transactions.controller;

import com.credit.card.fraud.detection.transactions.dto.ReportReviewRequest;
import com.credit.card.fraud.detection.transactions.dto.UserReportRequest;
import com.credit.card.fraud.detection.transactions.entity.FraudDetectionResult;
import com.credit.card.fraud.detection.transactions.entity.Transaction;
import com.credit.card.fraud.detection.transactions.entity.UserReport;
import com.credit.card.fraud.detection.transactions.repository.TransactionRepository;
import com.credit.card.fraud.detection.transactions.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
    private final TransactionRepository transactionRepository;

    @GetMapping
    @Operation(summary = "거래 목록 조회",
            description = "필터: userId, merchant, category, minAmount, maxAmount, isFraud, startTime, endTime")
    @Parameter(name = "userId", description = "사용자 ID", example = "USER_12345")
    @Parameter(name = "merchant", description = "가맹점명", example = "MERCHANT_789")
    @Parameter(name = "isFraud", description = "사기 여부", example = "false")
    @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(examples = @ExampleObject(value = """
            [{
              "id": 12345,
              "userId": "USER_12345", 
              "amount": 150.75,
              "merchant": "MERCHANT_789",
              "transactionTime": "2024-01-15T14:30:00",
              "isFraud": false
            }]""")))
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

        if (startTime == null) startTime = LocalDateTime.now().minusDays(7);
        if (endTime == null) endTime = LocalDateTime.now();

        List<Transaction> transactions = transactionService.getTransactionsWithFilters(
                userId, merchant, category, minAmount, maxAmount, isFraud, startTime, endTime, pageable);

        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/{transactionId}")
    @Operation(summary = "거래 상세 조회", description = "특정 거래의 상세 정보를 조회합니다")
    @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(examples = @ExampleObject(value = """
            {
              "id": 12345,
              "userId": "USER_12345",
              "amount": 150.75,
              "merchant": "MERCHANT_789", 
              "merchantCategory": "GROCERY",
              "transactionTime": "2024-01-15T14:30:00",
              "isFraud": false,
              "latitude": 37.5665,
              "longitude": 126.9780
            }""")))
    public ResponseEntity<Transaction> getTransaction(@PathVariable Long transactionId) {
        return transactionRepository.findById(transactionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{transactionId}/fraud-detection")
    @Operation(summary = "사기 탐지 결과 조회", description = "특정 거래의 사기 탐지 결과를 조회합니다")
    @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(examples = @ExampleObject(value = """
            {
              "lgbmScore": 0.234567,
              "xgboostScore": 0.345678, 
              "catboostScore": 0.456789,
              "finalScore": 0.312345,
              "finalPrediction": false,
              "predictionTime": "2024-01-15T14:30:01",
              "processingTimeMs": 245
            }""")))
    public ResponseEntity<FraudDetectionResult> getFraudDetectionResult(@PathVariable Long transactionId) {
        Optional<FraudDetectionResult> result = transactionService.getFraudDetectionResult(transactionId);
        return result.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{transactionId}/report")
    @Operation(summary = "사기 신고", description = "거래에 대한 사기 신고를 제출합니다")
    @ApiResponse(responseCode = "200", description = "신고 접수 성공",
            content = @Content(examples = @ExampleObject(value = """
            {
              "id": 789,
              "transactionId": 12345,
              "reportedBy": "user123",
              "reason": "UNAUTHORIZED_TRANSACTION",
              "description": "I did not make this transaction",
              "status": "PENDING"
            }""")))
    public ResponseEntity<UserReport> reportFraud(
            @PathVariable Long transactionId,
            @RequestBody @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(examples = @ExampleObject(value = """
                    {
                      "reportedBy": "user123",
                      "reason": "UNAUTHORIZED_TRANSACTION", 
                      "description": "I did not make this transaction"
                    }"""))) UserReportRequest request) {

        UserReport report = transactionService.reportFraud(
                transactionId, request.getReportedBy(), request.getReason(), request.getDescription());

        return ResponseEntity.ok(report);
    }

    @PutMapping("/reports/{reportId}/approve")
    @Operation(summary = "사기 신고 승인", description = "사기 신고를 승인하거나 거부합니다 (관리자 전용)")
    @ApiResponse(responseCode = "200", description = "처리 완료")
    public ResponseEntity<Void> approveReport(
            @PathVariable Long reportId,
            @RequestBody @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(examples = @ExampleObject(value = """
                    {
                      "reviewedBy": "admin123",
                      "comment": "Confirmed as fraud based on investigation",
                      "isFraud": true
                    }"""))) ReportReviewRequest request) {

        transactionService.approveReport(reportId, request.getReviewedBy(), request.getComment(), request.getIsFraud());
        return ResponseEntity.ok().build();
    }
}
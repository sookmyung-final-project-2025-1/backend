package com.credit.card.fraud.detection.transactions.controller;

import com.credit.card.fraud.detection.transactions.entity.FraudDetectionResult;
import com.credit.card.fraud.detection.transactions.entity.Transaction;
import com.credit.card.fraud.detection.transactions.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Tag(name = "거래 관리", description = "거래 조회 및 사기 탐지 API")
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping
    @Operation(summary = "거래 목록 조회",
            description = "다양한 필터 조건으로 거래 내역을 조회합니다. 기본적으로 최근 7일간의 데이터를 반환합니다.")
    @Parameter(name = "userId", description = "사용자 ID", example = "USER_12345")
    @Parameter(name = "merchant", description = "가맹점명 (부분 일치)", example = "STARBUCKS")
    @Parameter(name = "category", description = "가맹점 카테고리", example = "FOOD_BEVERAGE")
    @Parameter(name = "minAmount", description = "최소 금액", example = "1000")
    @Parameter(name = "maxAmount", description = "최대 금액", example = "100000")
    @Parameter(name = "isFraud", description = "사기 여부", example = "false")
    @Parameter(name = "startTime", description = "시작 시간 (ISO 8601)", example = "2024-01-01T00:00:00")
    @Parameter(name = "endTime", description = "종료 시간 (ISO 8601)", example = "2024-01-31T23:59:59")
    @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(examples = @ExampleObject(value = """
            {
              "content": [{
                "id": 12345,
                "userId": "USER_12345", 
                "amount": 150.75,
                "merchant": "STARBUCKS_GANGNAM",
                "merchantCategory": "FOOD_BEVERAGE",
                "transactionTime": "2024-01-15T14:30:00",
                "isFraud": false,
                "status": "PROCESSED"
              }],
              "pageable": {
                "pageNumber": 0,
                "pageSize": 50
              },
              "totalElements": 1,
              "totalPages": 1
            }""")))
    public ResponseEntity<Page<Transaction>> getTransactions(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String merchant,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) Boolean isFraud,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @PageableDefault(size = 50, sort = "transactionTime") Pageable pageable) {

        // 기본값 설정
        if (startTime == null) startTime = LocalDateTime.now().minusDays(7);
        if (endTime == null) endTime = LocalDateTime.now();

        Page<Transaction> transactions = transactionService.getTransactionsWithFilters(
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
              "merchant": "STARBUCKS_GANGNAM",
              "merchantCategory": "FOOD_BEVERAGE",
              "transactionTime": "2024-01-15T14:30:00",
              "virtualTime": "2024-01-15T14:30:00",
              "isFraud": false,
              "goldLabel": null,
              "latitude": 37.5665,
              "longitude": 126.9780,
              "deviceFingerprint": "DEVICE_ABC123",
              "status": "PROCESSED",
              "currency": "KRW"
            }""")))
    @ApiResponse(responseCode = "404", description = "거래를 찾을 수 없음")
    public ResponseEntity<Transaction> getTransaction(@PathVariable Long transactionId) {
        try {
            Transaction transaction = transactionService.getTransactionById(transactionId);
            return ResponseEntity.ok(transaction);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{transactionId}/fraud-detection")
    @Operation(summary = "사기 탐지 결과 조회", description = "특정 거래의 사기 탐지 결과를 조회합니다")
    @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(examples = @ExampleObject(value = """
            {
              "id": 1,
              "lgbmScore": 0.234567,
              "xgboostScore": 0.345678,
              "catboostScore": 0.456789,
              "finalScore": 0.312345,
              "finalPrediction": false,
              "confidenceScore": 0.892,
              "riskLevel": "MEDIUM",
              "threshold": 0.5,
              "predictionTime": "2024-01-15T14:30:01",
              "processingTimeMs": 245,
              "modelVersion": "v2.1.0"
            }""")))
    @ApiResponse(responseCode = "404", description = "탐지 결과를 찾을 수 없음")
    public ResponseEntity<FraudDetectionResult> getFraudDetectionResult(@PathVariable Long transactionId) {
        Optional<FraudDetectionResult> result = transactionService.getFraudDetectionResult(transactionId);
        return result.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "사용자별 거래 조회", description = "특정 사용자의 거래 내역을 조회합니다")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    public ResponseEntity<List<Transaction>> getTransactionsByUser(
            @PathVariable String userId,
            @PageableDefault(size = 20, sort = "transactionTime") Pageable pageable) {

        List<Transaction> transactions = transactionService.getTransactionsByUserId(userId, pageable);
        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/high-risk")
    @Operation(summary = "고위험 거래 조회", description = "사기 점수가 높은 거래들을 조회합니다")
    @Parameter(name = "minScore", description = "최소 사기 점수", example = "0.7")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    public ResponseEntity<List<Transaction>> getHighRiskTransactions(
            @RequestParam(required = false, defaultValue = "0.7") BigDecimal minScore,
            @PageableDefault(size = 50, sort = "createdAt") Pageable pageable) {

        List<Transaction> transactions = transactionService.getHighRiskTransactions(minScore, pageable);
        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/gold-label")
    @Operation(summary = "골드 라벨 거래 조회", description = "관리자가 검토한 골드 라벨이 있는 거래들을 조회합니다")
    @Parameter(name = "isFraud", description = "사기 여부 (null이면 모든 골드 라벨 거래)", example = "true")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    public ResponseEntity<List<Transaction>> getGoldLabelTransactions(
            @RequestParam(required = false) Boolean isFraud,
            @PageableDefault(size = 50, sort = "updatedAt") Pageable pageable) {

        List<Transaction> transactions = transactionService.getGoldLabelTransactions(isFraud, pageable);
        return ResponseEntity.ok(transactions);
    }
}
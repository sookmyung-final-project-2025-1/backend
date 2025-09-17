package com.credit.card.fraud.detection.transactions.controller;

import com.credit.card.fraud.detection.transactions.entity.Transaction;
import com.credit.card.fraud.detection.transactions.service.TransactionService;
import com.credit.card.fraud.detection.transactions.service.TransactionStreamingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
@Tag(name = "테스트 및 데모", description = "테스트 및 데모용 API")
public class TestController {

    private final TransactionService transactionService;
    private final TransactionStreamingService streamingService;

    @GetMapping("/health")
    @Operation(summary = "상태 확인", description = "기본 상태 확인 엔드포인트")
    @ApiResponse(responseCode = "200", description = "상태 확인 성공",
        content = @Content(mediaType = "application/json",
            examples = @ExampleObject(value = "{\"status\": \"OK\", \"timestamp\": \"2023-09-17T14:30:00\", \"service\": \"Credit Card Fraud Detection Platform\"}")))
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(Map.of(
            "status", "OK",
            "timestamp", LocalDateTime.now(),
            "service", "Credit Card Fraud Detection Platform"
        ));
    }

    @PostMapping("/create-sample-transaction")
    @Operation(summary = "샘플 거래 생성", description = "테스트용 샘플 거래를 생성합니다")
    public ResponseEntity<Transaction> createSampleTransaction() {
        Transaction transaction = Transaction.builder()
            .userId("USER_" + System.currentTimeMillis())
            .amount(BigDecimal.valueOf(Math.random() * 10000).setScale(2, BigDecimal.ROUND_HALF_UP))
            .merchant("TEST_MERCHANT")
            .merchantCategory("ONLINE_SHOPPING")
            .transactionTime(LocalDateTime.now())
            .virtualTime(LocalDateTime.now())
            .latitude(BigDecimal.valueOf(37.5665))
            .longitude(BigDecimal.valueOf(126.9780))
            .deviceFingerprint("TEST_DEVICE")
            .ipAddress("192.168.1.1")
            .externalTransactionId(UUID.randomUUID().toString())
            .build();

        Transaction processedTransaction = transactionService.processAndDetectFraud(transaction);
        return ResponseEntity.ok(processedTransaction);
    }

    @GetMapping("/streaming-status")
    @Operation(summary = "스트리밍 상태 조회", description = "테스트용 현재 스트리밍 상태를 조회합니다")
    @ApiResponse(responseCode = "200", description = "스트리밍 상태 조회 성공",
        content = @Content(mediaType = "application/json",
            examples = @ExampleObject(value = "{\"isStreaming\": true, \"mode\": \"TIMEMACHINE\", \"speed\": 10.0, \"currentTime\": \"2017-01-01T08:30:00\"}")))
    public ResponseEntity<Map<String, Object>> getStreamingStatus() {
        Map<String, Object> status = streamingService.getStreamingStatus();
        return ResponseEntity.ok(status);
    }

    @PostMapping("/start-demo")
    @Operation(summary = "데모 스트리밍 시작", description = "샘플 데이터로 데모 스트리밍을 시작합니다")
    @ApiResponse(responseCode = "200", description = "데모 시작 성공",
        content = @Content(mediaType = "application/json",
            examples = @ExampleObject(value = "{\"message\": \"Demo started successfully\", \"mode\": \"Time Machine (10x speed)\", \"startTime\": \"2017-01-01T00:00:00\"}")))
    public ResponseEntity<Map<String, String>> startDemo() {
        // 10배속 타임머신 모드로 데모 시작
        streamingService.startTimeMachineStreaming(
            LocalDateTime.of(2017, 1, 1, 0, 0), 
            10.0
        );
        
        return ResponseEntity.ok(Map.of(
            "message", "Demo started successfully",
            "mode", "Time Machine (10x speed)",
            "startTime", "2017-01-01T00:00:00"
        ));
    }
}
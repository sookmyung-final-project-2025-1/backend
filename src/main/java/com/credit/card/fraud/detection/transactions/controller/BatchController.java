package com.credit.card.fraud.detection.transactions.controller;

import com.credit.card.fraud.detection.transactions.service.CsvBatchService;
import com.credit.card.fraud.detection.transactions.service.BatchFraudDetectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/transactions/batch")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "배치 처리", description = "CSV 파일 배치 업로드 및 처리 API")
public class BatchController {

    private final CsvBatchService csvBatchService;
    private final BatchFraudDetectionService batchFraudDetectionService;

    @PostMapping("/upload")
    @Operation(summary = "CSV 파일 업로드", description = "IEEE 신용카드 데이터셋 CSV 파일을 업로드하여 배치 처리를 시작합니다")
    public ResponseEntity<Map<String, Object>> uploadCsvFile(@RequestParam("file") MultipartFile file) {
        try {
            log.info("파일 업로드 요청 시작 - 파일명: {}, 크기: {} bytes",
                file.getOriginalFilename(), file.getSize());

            // 파일 유효성 검사
            if (file == null || file.isEmpty()) {
                log.warn("빈 파일 업로드 시도");
                return ResponseEntity.badRequest()
                        .header("Content-Type", "application/json;charset=UTF-8")
                        .body(Map.of("error", "파일이 비어있습니다"));
            }

            if (file.getOriginalFilename() == null ||
                !file.getOriginalFilename().toLowerCase().endsWith(".csv")) {
                log.warn("잘못된 파일 형식: {}", file.getOriginalFilename());
                return ResponseEntity.badRequest()
                        .header("Content-Type", "application/json;charset=UTF-8")
                        .body(Map.of("error", "CSV 파일만 업로드 가능합니다"));
            }

            // 파일 크기 검증 (1GB 제한)
            if (file.getSize() > 1024L * 1024 * 1024) {
                log.warn("파일 크기 초과: {} bytes", file.getSize());
                return ResponseEntity.badRequest()
                        .header("Content-Type", "application/json;charset=UTF-8")
                        .body(Map.of("error", "파일 크기는 1GB를 초과할 수 없습니다"));
            }

            // 비동기 배치 처리 시작
            String jobId = csvBatchService.startBatchProcessing(file);
            log.info("배치 처리 시작됨 - Job ID: {}", jobId);

            Map<String, Object> response = Map.of(
                "jobId", jobId,
                "message", "배치 처리가 시작되었습니다",
                "fileName", file.getOriginalFilename(),
                "fileSize", file.getSize()
            );

            return ResponseEntity.ok()
                    .header("Content-Type", "application/json;charset=UTF-8")
                    .body(response);

        } catch (Exception e) {
            log.error("CSV 파일 업로드 실패", e);
            return ResponseEntity.internalServerError()
                    .header("Content-Type", "application/json;charset=UTF-8")
                    .body(Map.of("error", "파일 업로드 실패: " + e.getMessage()));
        }
    }

    @GetMapping("/status/{jobId}")
    @Operation(summary = "배치 작업 상태 조회", description = "배치 처리 작업의 진행 상태를 조회합니다")
    public ResponseEntity<Map<String, Object>> getBatchStatus(@PathVariable String jobId) {
        try {
            Map<String, Object> status = csvBatchService.getBatchStatus(jobId);

            if (status.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(status);

        } catch (Exception e) {
            log.error("배치 상태 조회 실패: {}", jobId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "상태 조회 실패: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/process-fraud-detection")
    @Operation(summary = "PENDING 상태 거래 사기 탐지 처리", description = "업로드된 PENDING 상태 거래들에 대해 사기 탐지를 수행합니다")
    public ResponseEntity<Map<String, Object>> processFraudDetection(
            @RequestParam(value = "limit", required = false) Integer limit) {
        try {
            long pendingCount = batchFraudDetectionService.getPendingTransactionCount();

            if (pendingCount == 0) {
                return ResponseEntity.ok(Map.of(
                    "message", "처리할 PENDING 상태 거래가 없습니다",
                    "pendingCount", 0
                ));
            }

            CompletableFuture<Integer> futureResult;
            if (limit != null && limit > 0) {
                futureResult = batchFraudDetectionService.processPendingTransactions(limit);
            } else {
                batchFraudDetectionService.processPendingTransactions();
                futureResult = CompletableFuture.completedFuture((int) pendingCount);
            }

            Map<String, Object> response = Map.of(
                "message", "사기 탐지 처리가 시작되었습니다",
                "pendingCount", pendingCount,
                "requestedLimit", limit != null ? limit : "전체"
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("사기 탐지 처리 시작 실패", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "사기 탐지 처리 시작 실패: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/pending-count")
    @Operation(summary = "PENDING 상태 거래 개수 조회", description = "사기 탐지 처리를 기다리고 있는 거래의 개수를 조회합니다")
    public ResponseEntity<Map<String, Object>> getPendingCount() {
        try {
            long pendingCount = batchFraudDetectionService.getPendingTransactionCount();

            return ResponseEntity.ok(Map.of(
                "pendingCount", pendingCount,
                "message", pendingCount > 0 ?
                    "사기 탐지 처리를 기다리는 거래가 " + pendingCount + "건 있습니다" :
                    "처리할 PENDING 상태 거래가 없습니다"
            ));

        } catch (Exception e) {
            log.error("PENDING 거래 개수 조회 실패", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "PENDING 거래 개수 조회 실패: " + e.getMessage()
            ));
        }
    }

    @DeleteMapping("/clear-batch-data")
    @Operation(summary = "배치 업로드 데이터 삭제", description = "배치 업로드로 삽입된 모든 거래 데이터를 삭제합니다 (PENDING 상태 거래 대상)")
    public ResponseEntity<Map<String, Object>> clearBatchData() {
        try {
            long deletedCount = csvBatchService.clearBatchData();

            return ResponseEntity.ok(Map.of(
                "message", "배치 데이터 삭제 완료",
                "deletedCount", deletedCount
            ));

        } catch (Exception e) {
            log.error("배치 데이터 삭제 실패", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "배치 데이터 삭제 실패: " + e.getMessage()
            ));
        }
    }
}
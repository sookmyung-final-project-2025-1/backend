package com.credit.card.fraud.detection.transactions.controller;

import com.credit.card.fraud.detection.transactions.service.CsvBatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/transactions/batch")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "배치 처리", description = "CSV 파일 배치 업로드 및 처리 API")
public class BatchController {

    private final CsvBatchService csvBatchService;

    @PostMapping("/upload")
    @Operation(summary = "CSV 파일 업로드", description = "IEEE 신용카드 데이터셋 CSV 파일을 업로드하여 배치 처리를 시작합니다")
    public ResponseEntity<Map<String, Object>> uploadCsvFile(@RequestParam("file") MultipartFile file) {
        try {
            // 파일 유효성 검사
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "파일이 비어있습니다"));
            }

            if (!file.getOriginalFilename().toLowerCase().endsWith(".csv")) {
                return ResponseEntity.badRequest().body(Map.of("error", "CSV 파일만 업로드 가능합니다"));
            }

            // 비동기 배치 처리 시작
            String jobId = csvBatchService.startBatchProcessing(file);

            Map<String, Object> response = Map.of(
                "jobId", jobId,
                "message", "배치 처리가 시작되었습니다",
                "fileName", file.getOriginalFilename(),
                "fileSize", file.getSize()
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("CSV 파일 업로드 실패", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "파일 업로드 실패: " + e.getMessage()
            ));
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
}
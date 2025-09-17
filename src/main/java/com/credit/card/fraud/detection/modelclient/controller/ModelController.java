package com.credit.card.fraud.detection.modelclient.controller;

import com.credit.card.fraud.detection.modelclient.dto.ConfidenceScoreResponse;
import com.credit.card.fraud.detection.modelclient.dto.ModelWeightsUpdateRequest;
import com.credit.card.fraud.detection.modelclient.service.EnsembleModelService;
import com.credit.card.fraud.detection.transactions.repository.FraudDetectionResultRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/model")
@RequiredArgsConstructor
@Tag(name = "모델 관리", description = "모델 설정 및 모니터링 API")
public class ModelController {

    private final EnsembleModelService ensembleModelService;
    private final FraudDetectionResultRepository fraudDetectionResultRepository;

    @GetMapping("/weights")
    @Operation(summary = "현재 모델 가중치 조회", description = "앙상블 모델의 현재 가중치를 조회합니다")
    @ApiResponse(responseCode = "200", description = "가중치 조회 성공",
        content = @Content(mediaType = "application/json",
            examples = @ExampleObject(value = "{\"lgbm\": 0.4, \"xgboost\": 0.35, \"catboost\": 0.25, \"threshold\": 0.5}")))
    public ResponseEntity<Map<String, BigDecimal>> getCurrentWeights() {
        Map<String, BigDecimal> weights = ensembleModelService.getCurrentWeights();
        weights.put("threshold", ensembleModelService.getCurrentThreshold());
        return ResponseEntity.ok(weights);
    }

    @PutMapping("/weights")
    @Operation(summary = "모델 가중치 업데이트", description = "앙상블 모델의 가중치를 업데이트합니다")
    @ApiResponse(responseCode = "200", description = "가중치 업데이트 성공",
        content = @Content(mediaType = "application/json",
            examples = @ExampleObject(value = "{\"status\": \"Weights updated successfully\", \"weights\": {\"lgbm\": 0.4, \"xgboost\": 0.35, \"catboost\": 0.25}}")))
    public ResponseEntity<Map<String, Object>> updateWeights(@Valid @RequestBody ModelWeightsUpdateRequest request) {
        ensembleModelService.updateWeights(request);
        
        Map<String, Object> response = Map.of(
            "status", "Weights updated successfully",
            "weights", Map.of(
                "lgbm", request.getLgbmWeight(),
                "xgboost", request.getXgboostWeight(),
                "catboost", request.getCatboostWeight()
            )
        );
        
        return ResponseEntity.ok(response);
    }

    @PutMapping("/threshold")
    @Operation(summary = "예측 임계값 업데이트", description = "사기 예측 임계값을 업데이트합니다")
    @ApiResponse(responseCode = "200", description = "임계값 업데이트 성공",
        content = @Content(mediaType = "application/json",
            examples = @ExampleObject(value = "{\"status\": \"Threshold updated successfully\", \"threshold\": 0.5}")))
    public ResponseEntity<Map<String, Object>> updateThreshold(@RequestParam BigDecimal threshold) {
        ensembleModelService.updateThreshold(threshold);
        
        return ResponseEntity.ok(Map.of(
            "status", "Threshold updated successfully",
            "threshold", threshold
        ));
    }

    @GetMapping("/confidence-score")
    @Operation(summary = "신뢰도 점수 조회", description = "현재 신뢰도 점수와 시계열 데이터를 조회합니다")
    public ResponseEntity<ConfidenceScoreResponse> getConfidenceScore(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "hourly") String period) {

        if (startTime == null) {
            startTime = LocalDateTime.now().minusHours(24);
        }
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }

        // 현재 confidence score 계산
        BigDecimal currentScore = fraudDetectionResultRepository.averageConfidenceScore(startTime, endTime);
        if (currentScore == null) {
            currentScore = BigDecimal.ZERO;
        }

        // 시계열 데이터 생성
        List<Object[]> hourlyStats = fraudDetectionResultRepository.getHourlyStats(startTime, endTime);
        List<ConfidenceScoreResponse.TimeSeriesPoint> timeSeries = hourlyStats.stream()
            .map(row -> ConfidenceScoreResponse.TimeSeriesPoint.builder()
                .timestamp((LocalDateTime) row[0])
                .confidenceScore((BigDecimal) row[1])
                .transactionCount((Long) row[2])
                .period(period)
                .build())
            .toList();

        ConfidenceScoreResponse response = ConfidenceScoreResponse.of(currentScore, timeSeries);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/retrain")
    @Operation(summary = "모델 재학습 시작", description = "새로운 골드 라벨로 모델 재학습을 시작합니다 (시뮬레이션)")
    @ApiResponse(responseCode = "200", description = "재학습 시작 성공",
        content = @Content(mediaType = "application/json",
            examples = @ExampleObject(value = "{\"status\": \"Model retraining triggered\", \"message\": \"Retraining will start with updated gold labels\", \"estimatedTime\": \"15-30 minutes\"}")))
    public ResponseEntity<Map<String, String>> triggerRetraining() {
        // 실제 환경에서는 여기서 모델 재학습 파이프라인을 시작
        // 현재는 시뮬레이션만 수행
        
        return ResponseEntity.ok(Map.of(
            "status", "Model retraining triggered",
            "message", "Retraining will start with updated gold labels",
            "estimatedTime", "15-30 minutes"
        ));
    }

    @GetMapping("/feature-importance")
    @Operation(summary = "피처 중요도 조회", description = "최근 예측 결과들의 평균 피처 중요도를 조회합니다")
    @ApiResponse(responseCode = "200", description = "피처 중요도 조회 성공",
        content = @Content(mediaType = "application/json",
            examples = @ExampleObject(value = "{\"featureImportance\": {\"amount\": 0.20, \"merchant_category\": 0.14, \"C1\": 0.15}, \"sampleSize\": 1000, \"calculatedAt\": \"2025-09-17T12:00:00\"}")))
    public ResponseEntity<Map<String, Object>> getFeatureImportance(
            @RequestParam(defaultValue = "1000") Integer sampleSize) {
        
        // 실제 구현에서는 최근 예측 결과들에서 피처 중요도를 집계
        // 현재는 시뮬레이션된 데이터 반환
        Map<String, Double> featureImportance = Map.of(
            "C1", 0.15,
            "C2", 0.12,
            "C3", 0.08,
            "C4", 0.11,
            "amount", 0.20,
            "hour", 0.09,
            "merchant_category", 0.14,
            "location", 0.11
        );
        
        return ResponseEntity.ok(Map.of(
            "featureImportance", featureImportance,
            "sampleSize", sampleSize,
            "calculatedAt", LocalDateTime.now()
        ));
    }
}
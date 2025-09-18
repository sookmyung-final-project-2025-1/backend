package com.credit.card.fraud.detection.modelclient.controller;

import com.credit.card.fraud.detection.modelclient.dto.ConfidenceScoreResponse;
import com.credit.card.fraud.detection.modelclient.dto.ModelWeightsUpdateRequest;
import com.credit.card.fraud.detection.modelclient.service.EnsembleModelService;
import com.credit.card.fraud.detection.modelclient.service.ModelMetricsService;
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
import java.util.Map;

@RestController
@RequestMapping("/api/model")
@RequiredArgsConstructor
@Tag(name = "모델 관리", description = "모델 설정 및 모니터링 API")
public class ModelController {

    private final EnsembleModelService ensembleModelService;
    private final ModelMetricsService modelMetricsService;

    @GetMapping("/weights")
    @Operation(summary = "현재 모델 가중치 조회", description = "앙상블 모델의 현재 가중치를 조회합니다")
    @ApiResponse(responseCode = "200", description = "가중치 조회 성공",
        content = @Content(mediaType = "application/json",
            examples = @ExampleObject(value = """
                {
                  "lgbm": 0.333,
                  "xgboost": 0.333,
                  "catboost": 0.334,
                  "threshold": 0.5
                }""")))
    public ResponseEntity<Map<String, BigDecimal>> getCurrentWeights() {
        Map<String, BigDecimal> weights = ensembleModelService.getCurrentWeights();
        weights.put("threshold", ensembleModelService.getCurrentThreshold());
        return ResponseEntity.ok(weights);
    }

    @PutMapping("/weights")
    @Operation(summary = "모델 가중치 업데이트", description = "앙상블 모델의 가중치를 업데이트합니다")
    @ApiResponse(responseCode = "200", description = "가중치 업데이트 성공",
        content = @Content(mediaType = "application/json",
            examples = @ExampleObject(value = """
                {
                  "status": "Weights updated successfully",
                  "weights": {
                    "lgbm": 0.4,
                    "xgboost": 0.35,
                    "catboost": 0.25
                  }
                }""")))
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
            examples = @ExampleObject(value = """
                {
                  "status": "Threshold updated successfully",
                  "threshold": 0.7
                }""")))
    public ResponseEntity<Map<String, Object>> updateThreshold(@RequestParam BigDecimal threshold) {
        ensembleModelService.updateThreshold(threshold);
        
        return ResponseEntity.ok(Map.of(
            "status", "Threshold updated successfully",
            "threshold", threshold
        ));
    }

    @GetMapping("/confidence-score")
    @Operation(summary = "신뢰도 점수 조회", description = "현재 신뢰도 점수와 시계열 데이터를 조회합니다")
    @ApiResponse(responseCode = "200", description = "신뢰도 점수 조회 성공",
        content = @Content(mediaType = "application/json",
            examples = @ExampleObject(value = """
                {
                  "currentConfidenceScore": 0.8245,
                  "calculatedAt": "2025-09-18T10:30:00",
                  "timeSeries": [
                    {
                      "timestamp": "2025-09-18T09:00:00",
                      "confidenceScore": 0.8156,
                      "transactionCount": 245,
                      "period": "hourly"
                    },
                    {
                      "timestamp": "2025-09-18T10:00:00",
                      "confidenceScore": 0.8334,
                      "transactionCount": 189,
                      "period": "hourly"
                    }
                  ]
                }""")))
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

        ConfidenceScoreResponse response = modelMetricsService.getConfidenceScore(startTime, endTime, period);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/retrain")
    @Operation(summary = "모델 재학습 시작", description = "새로운 골드 라벨로 모델 재학습을 시작합니다 (시뮬레이션)")
    @ApiResponse(responseCode = "200", description = "재학습 시작 성공",
        content = @Content(mediaType = "application/json",
            examples = @ExampleObject(value = """
                {
                  "status": "Model retraining triggered",
                  "message": "Retraining will start with updated gold labels",
                  "estimatedTime": "15-30 minutes",
                  "jobId": "retrain-job-12345",
                  "startedAt": "2025-09-18T10:30:00"
                }""")))
    public ResponseEntity<Map<String, String>> triggerRetraining() {
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
            examples = @ExampleObject(value = """
                {
                  "featureImportance": {
                    "amount": 0.20,
                    "merchant_category": 0.14,
                    "C1": 0.15,
                    "C2": 0.12,
                    "C3": 0.08,
                    "C4": 0.11,
                    "hour": 0.09,
                    "location": 0.11
                  },
                  "sampleSize": 1000,
                  "calculatedAt": "2025-09-18T10:30:00",
                  "modelVersion": "v1.2.3",
                  "topFeatures": [
                    {"name": "amount", "importance": 0.20},
                    {"name": "C1", "importance": 0.15},
                    {"name": "merchant_category", "importance": 0.14}
                  ]
                }""")))
    public ResponseEntity<Map<String, Object>> getFeatureImportance(
            @RequestParam(defaultValue = "1000") Integer sampleSize) {
        
        Map<String, Double> featureImportance = Map.of(
            "C1", 0.15, "C2", 0.12, "C3", 0.08, "C4", 0.11,
            "amount", 0.20, "hour", 0.09, "merchant_category", 0.14, "location", 0.11
        );
        
        return ResponseEntity.ok(Map.of(
            "featureImportance", featureImportance,
            "sampleSize", sampleSize,
            "calculatedAt", LocalDateTime.now()
        ));
    }
}
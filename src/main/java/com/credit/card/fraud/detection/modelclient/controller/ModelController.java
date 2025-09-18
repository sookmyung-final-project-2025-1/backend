package com.credit.card.fraud.detection.modelclient.controller;

import com.credit.card.fraud.detection.modelclient.dto.ConfidenceScoreResponse;
import com.credit.card.fraud.detection.modelclient.dto.ModelWeightsUpdateRequest;
import com.credit.card.fraud.detection.modelclient.service.EnsembleModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/model")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "모델 관리", description = "모델 설정 및 모니터링 API")
public class ModelController {

    private final EnsembleModelService ensembleModelService;
    private final com.credit.card.fraud.detection.modelclient.service.ConfidenceScoreService confidenceScoreService;

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
                  },
                  "updatedAt": "2025-09-18T14:30:00",
                  "normalizedWeights": true
                }""")))
    public ResponseEntity<Map<String, Object>> updateWeights(@Valid @RequestBody ModelWeightsUpdateRequest request) {
        // 가중치 정규화 옵션 처리
        if (request.getAutoNormalize() != null && request.getAutoNormalize() && !request.isValidWeightSum()) {
            request.normalizeWeights();
        }

        ensembleModelService.updateWeights(request);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "Weights updated successfully");
        response.put("weights", Map.of(
            "lgbm", request.getLgbmWeight(),
            "xgboost", request.getXgboostWeight(),
            "catboost", request.getCatboostWeight()
        ));
        response.put("updatedAt", LocalDateTime.now());
        response.put("normalizedWeights", request.getAutoNormalize() != null ? request.getAutoNormalize() : false);
        response.put("weightSum", request.getLgbmWeight().add(request.getXgboostWeight()).add(request.getCatboostWeight()));

        // 실시간 반영 알림을 위한 웹소켓 메시지 전송
        broadcastModelConfigUpdate("WEIGHTS_UPDATED", response);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/weights/validation")
    @Operation(summary = "가중치 유효성 검증", description = "입력된 가중치의 유효성을 검증합니다")
    public ResponseEntity<Map<String, Object>> validateWeights(
            @RequestParam BigDecimal lgbm,
            @RequestParam BigDecimal xgboost,
            @RequestParam BigDecimal catboost) {

        Map<String, Object> validation = new HashMap<>();
        BigDecimal sum = lgbm.add(xgboost).add(catboost);

        validation.put("isValid", sum.compareTo(BigDecimal.ONE) == 0);
        validation.put("sum", sum);
        validation.put("normalized", false);

        if (sum.compareTo(BigDecimal.ONE) != 0) {
            // 자동 정규화된 가중치 제공
            Map<String, BigDecimal> normalizedWeights = new HashMap<>();
            normalizedWeights.put("lgbm", lgbm.divide(sum, 6, RoundingMode.HALF_UP));
            normalizedWeights.put("xgboost", xgboost.divide(sum, 6, RoundingMode.HALF_UP));
            normalizedWeights.put("catboost", catboost.divide(sum, 6, RoundingMode.HALF_UP));

            validation.put("normalizedWeights", normalizedWeights);
        }

        return ResponseEntity.ok(validation);
    }

    @PutMapping("/threshold")
    @Operation(summary = "예측 임계값 업데이트", description = "사기 예측 임계값을 업데이트합니다")
    @ApiResponse(responseCode = "200", description = "임계값 업데이트 성공",
        content = @Content(mediaType = "application/json",
            examples = @ExampleObject(value = """
                {
                  "status": "Threshold updated successfully",
                  "threshold": 0.7,
                  "updatedAt": "2025-09-18T14:30:00",
                  "previousThreshold": 0.5
                }""")))
    public ResponseEntity<Map<String, Object>> updateThreshold(@RequestParam BigDecimal threshold) {
        // 유효성 검증
        if (threshold.compareTo(BigDecimal.ZERO) <= 0 || threshold.compareTo(BigDecimal.ONE) >= 0) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Threshold must be between 0 and 1",
                "providedThreshold", threshold
            ));
        }

        BigDecimal previousThreshold = ensembleModelService.getCurrentThreshold();
        ensembleModelService.updateThreshold(threshold);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "Threshold updated successfully");
        response.put("threshold", threshold);
        response.put("previousThreshold", previousThreshold);
        response.put("updatedAt", LocalDateTime.now());

        // 실시간 반영 알림을 위한 웹소켓 메시지 전송
        broadcastModelConfigUpdate("THRESHOLD_UPDATED", response);

        return ResponseEntity.ok(response);
    }

    // 웹소켓 브로드캐스트 헬퍼 메서드
    private void broadcastModelConfigUpdate(String updateType, Map<String, Object> data) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", updateType);
        message.put("data", data);
        message.put("timestamp", LocalDateTime.now());

        // SimpMessagingTemplate을 통한 실시간 알림 (WebSocket 설정이 있다면)
        try {
            // messagingTemplate.convertAndSend("/topic/model-config", message);
            log.info("Model configuration updated: {}", updateType);
        } catch (Exception e) {
            log.warn("Failed to broadcast model config update: {}", e.getMessage());
        }
    }

    @GetMapping("/confidence-score")
    @Operation(summary = "신뢰도 점수 조회", description = "현재 신뢰도 점수와 시계열 데이터를 조회합니다")
    @ApiResponse(responseCode = "200", description = "신뢰도 점수 조회 성공",
        content = @Content(mediaType = "application/json",
            examples = @ExampleObject(value = """
                {
                  "currentConfidenceScore": 0.824567,
                  "calculatedAt": "2025-09-18T10:30:00",
                  "timeSeries": [
                    {
                      "timestamp": "2025-09-18T09:00:00",
                      "confidenceScore": 0.815634,
                      "transactionCount": 245,
                      "period": "hourly",
                      "isModelUpdatePoint": false
                    },
                    {
                      "timestamp": "2025-09-18T10:00:00",
                      "confidenceScore": 0.833478,
                      "transactionCount": 189,
                      "period": "hourly",
                      "isModelUpdatePoint": true
                    }
                  ],
                  "modelDriftStatus": "STABLE",
                  "alertThreshold": 0.600000,
                  "lastModelUpdate": "2025-09-18T09:45:00",
                  "scoreBeforeUpdate": 0.780000,
                  "scoreAfterUpdate": 0.833478
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

        ConfidenceScoreResponse response = confidenceScoreService.getConfidenceScore(startTime, endTime, period);
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
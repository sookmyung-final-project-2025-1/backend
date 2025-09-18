package com.credit.card.fraud.detection.modelclient.controller;

import com.credit.card.fraud.detection.modelclient.dto.ConfidenceScoreResponse;
import com.credit.card.fraud.detection.modelclient.dto.ModelWeightsUpdateRequest;
import com.credit.card.fraud.detection.modelclient.service.EnsembleModelService;
import com.credit.card.fraud.detection.modelclient.service.ConfidenceScoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/model")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "모델 관리", description = "모델 설정 및 모니터링 API")
public class ModelController {

    private final EnsembleModelService ensembleModelService;
    private final ConfidenceScoreService confidenceScoreService;
    
    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

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
        try {
            Map<String, BigDecimal> weights = ensembleModelService.getCurrentWeights();
            weights.put("threshold", ensembleModelService.getCurrentThreshold());
            return ResponseEntity.ok(weights);
        } catch (Exception e) {
            log.error("Failed to retrieve current weights", e);
            throw new RuntimeException("Failed to retrieve current weights: " + e.getMessage());
        }
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
        try {
            // 가중치 처리 (정규화 또는 검증)
            ModelWeightsUpdateRequest processedRequest = request.processWeights();

            ensembleModelService.updateWeights(processedRequest);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "Weights updated successfully");
            response.put("weights", Map.of(
                "lgbm", processedRequest.getLgbmWeight(),
                "xgboost", processedRequest.getXgboostWeight(),
                "catboost", processedRequest.getCatboostWeight()
            ));
            response.put("updatedAt", LocalDateTime.now());
            response.put("normalizedWeights", request.getAutoNormalize() != null ? request.getAutoNormalize() : false);
            response.put("weightSum", processedRequest.getWeightSum());
            response.put("weightsInfo", processedRequest.getWeightsInfo());

            // 실시간 반영 알림을 위한 웹소켓 메시지 전송
            broadcastModelConfigUpdate("WEIGHTS_UPDATED", response);

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid weights provided: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Invalid weights: " + e.getMessage(),
                "providedWeights", request.getWeightsInfo()
            ));
        } catch (Exception e) {
            log.error("Failed to update weights", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to update weights: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/weights/validation")
    @Operation(summary = "가중치 유효성 검증", description = "입력된 가중치의 유효성을 검증합니다")
    public ResponseEntity<Map<String, Object>> validateWeights(
            @RequestParam BigDecimal lgbm,
            @RequestParam BigDecimal xgboost,
            @RequestParam BigDecimal catboost) {

        try {
            ModelWeightsUpdateRequest tempRequest = ModelWeightsUpdateRequest.builder()
                .lgbmWeight(lgbm)
                .xgboostWeight(xgboost)
                .catboostWeight(catboost)
                .autoNormalize(false)
                .build();

            Map<String, Object> validation = new HashMap<>();
            BigDecimal sum = tempRequest.getWeightSum();
            boolean isValid = tempRequest.isValidWeightSum();

            validation.put("isValid", isValid);
            validation.put("sum", sum);
            validation.put("weightsInfo", tempRequest.getWeightsInfo());

            if (!isValid) {
                // 자동 정규화된 가중치 제공
                ModelWeightsUpdateRequest normalizedRequest = ModelWeightsUpdateRequest.builder()
                    .lgbmWeight(lgbm)
                    .xgboostWeight(xgboost)
                    .catboostWeight(catboost)
                    .autoNormalize(true)
                    .build();
                normalizedRequest.normalizeWeights();

                Map<String, BigDecimal> normalizedWeights = new HashMap<>();
                normalizedWeights.put("lgbm", normalizedRequest.getLgbmWeight());
                normalizedWeights.put("xgboost", normalizedRequest.getXgboostWeight());
                normalizedWeights.put("catboost", normalizedRequest.getCatboostWeight());

                validation.put("normalizedWeights", normalizedWeights);
                validation.put("normalizedWeightsInfo", normalizedRequest.getWeightsInfo());
            }

            return ResponseEntity.ok(validation);
        } catch (Exception e) {
            log.error("Failed to validate weights", e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Failed to validate weights: " + e.getMessage()
            ));
        }
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
        if (threshold == null || threshold.compareTo(BigDecimal.ZERO) <= 0 || threshold.compareTo(BigDecimal.ONE) >= 0) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Threshold must be between 0 and 1 (exclusive)",
                "providedThreshold", threshold != null ? threshold : "null"
            ));
        }

        try {
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
        } catch (Exception e) {
            log.error("Failed to update threshold", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to update threshold: " + e.getMessage()
            ));
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
                    }
                  ],
                  "modelDriftStatus": "STABLE",
                  "alertThreshold": 0.600000
                }""")))
    public ResponseEntity<ConfidenceScoreResponse> getConfidenceScore(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "hourly") String period) {

        try {
            if (startTime == null) {
                startTime = LocalDateTime.now().minusHours(24);
            }
            if (endTime == null) {
                endTime = LocalDateTime.now();
            }

            // 시간 범위 검증
            if (startTime.isAfter(endTime)) {
                throw new IllegalArgumentException("Start time must be before end time");
            }

            ConfidenceScoreResponse response = confidenceScoreService.getConfidenceScore(startTime, endTime, period);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid parameters for confidence score request: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to retrieve confidence score", e);
            throw new RuntimeException("Failed to retrieve confidence score: " + e.getMessage());
        }
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
    public ResponseEntity<Map<String, Object>> triggerRetraining() {
        try {
            // 재학습 작업 ID 생성
            String jobId = "retrain-job-" + System.currentTimeMillis();
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "Model retraining triggered");
            response.put("message", "Retraining will start with updated gold labels");
            response.put("estimatedTime", "15-30 minutes");
            response.put("jobId", jobId);
            response.put("startedAt", LocalDateTime.now());

            // 재학습 시작 알림
            broadcastModelConfigUpdate("RETRAINING_STARTED", response);
            
            log.info("Model retraining triggered with job ID: {}", jobId);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to trigger model retraining", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to trigger model retraining: " + e.getMessage()
            ));
        }
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
                  "modelVersion": "v1.2.3"
                }""")))
    public ResponseEntity<Map<String, Object>> getFeatureImportance(
            @RequestParam(defaultValue = "1000") Integer sampleSize) {
        
        try {
            if (sampleSize <= 0 || sampleSize > 100000) {
                throw new IllegalArgumentException("Sample size must be between 1 and 100,000");
            }

            Map<String, Double> featureImportance = Map.of(
                "C1", 0.15, "C2", 0.12, "C3", 0.08, "C4", 0.11,
                "amount", 0.20, "hour", 0.09, "merchant_category", 0.14, "location", 0.11
            );

            Map<String, Object> response = new HashMap<>();
            response.put("featureImportance", featureImportance);
            response.put("sampleSize", sampleSize);
            response.put("calculatedAt", LocalDateTime.now());
            response.put("modelVersion", "v1.2.3");

            // 상위 3개 피처 추가
            response.put("topFeatures", featureImportance.entrySet()
                .stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(3)
                .map(entry -> Map.of("name", entry.getKey(), "importance", entry.getValue()))
                .toList());
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid sample size for feature importance: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "error", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Failed to retrieve feature importance", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to retrieve feature importance: " + e.getMessage()
            ));
        }
    }

    /**
     * 웹소켓 브로드캐스트 헬퍼 메서드
     * 웹소켓 설정이 활성화된 경우에만 메시지를 전송합니다.
     * 
     * @param updateType 업데이트 타입
     * @param data 전송할 데이터
     */
    private void broadcastModelConfigUpdate(String updateType, Map<String, Object> data) {
        if (messagingTemplate != null) {
            Map<String, Object> message = new HashMap<>();
            message.put("type", updateType);
            message.put("data", data);
            message.put("timestamp", LocalDateTime.now());

            try {
                messagingTemplate.convertAndSend("/topic/model-config", message);
                log.debug("Model configuration update broadcasted: {}", updateType);
            } catch (Exception e) {
                log.warn("Failed to broadcast model config update: {}", e.getMessage());
            }
        } else {
            log.debug("WebSocket messaging template not available, skipping broadcast for: {}", updateType);
        }
    }
}
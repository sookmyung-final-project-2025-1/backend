package com.credit.card.fraud.detection.modelclient.service;

import com.credit.card.fraud.detection.modelclient.dto.ModelPredictionRequest;
import com.credit.card.fraud.detection.modelclient.dto.ModelPredictionResponse;
import com.credit.card.fraud.detection.global.config.ModelServiceProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class ModelServiceClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    private final ModelServiceProperties modelServiceProperties;
    private final ModelVersionService modelVersionService;

    public ModelServiceClient(RestTemplate restTemplate, ObjectMapper objectMapper,
                             ModelServiceProperties modelServiceProperties,
                             ModelVersionService modelVersionService) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.modelServiceProperties = modelServiceProperties;
        this.modelVersionService = modelVersionService;
    }

    /**
     * 앙상블 모델 예측 (3개 모델 통합)
     */
    public ModelPredictionResponse predictEnsemble(ModelPredictionRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            // 3개 모델에 병렬로 요청
            CompletableFuture<BigDecimal> lgbmFuture = CompletableFuture.supplyAsync(
                () -> predictSingleModel(request, "lgbm"), executorService);
            CompletableFuture<BigDecimal> xgboostFuture = CompletableFuture.supplyAsync(
                () -> predictSingleModel(request, "xgboost"), executorService);
            CompletableFuture<BigDecimal> catboostFuture = CompletableFuture.supplyAsync(
                () -> predictSingleModel(request, "catboost"), executorService);

            // 모든 모델 결과 대기
            BigDecimal lgbmScore = lgbmFuture.get();
            BigDecimal xgboostScore = xgboostFuture.get();
            BigDecimal catboostScore = catboostFuture.get();

            long processingTime = System.currentTimeMillis() - startTime;

            // 가중치 적용하여 최종 점수 계산
            Map<String, BigDecimal> weights = request.getModelWeights();
            BigDecimal finalScore = calculateWeightedScore(
                lgbmScore, xgboostScore, catboostScore, weights);

            // 예측 결과 생성
            boolean finalPrediction = finalScore.compareTo(request.getThreshold()) > 0;

            return ModelPredictionResponse.success(
                request.getTransactionId(),
                lgbmScore,
                xgboostScore,
                catboostScore,
                weights.get("lgbm"),
                weights.get("xgboost"),
                weights.get("catboost"),
                request.getThreshold(),
                processingTime
            );

        } catch (Exception e) {
            log.error("Ensemble prediction failed for transaction {}: {}",
                request.getTransactionId(), e.getMessage(), e);
            return ModelPredictionResponse.error(request.getTransactionId(), e.getMessage());
        }
    }

    /**
     * 단일 모델 예측
     */
    public BigDecimal predictSingleModel(ModelPredictionRequest request, String modelType) {
        try {
            // FastAPI 엔드포인트 매핑: lgbm -> lgbm, xgboost -> xgboost, catboost -> catboost
            String apiModelType = mapModelType(modelType);
            String endpoint = String.format("%s/model/%s/predict", modelServiceProperties.getUrl(), apiModelType);

            // 요청 데이터 구성
            Map<String, Object> requestData = buildModelRequest(request);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestData, headers);

            log.debug("Calling {} model API: {}", modelType, endpoint);

            ResponseEntity<String> response = restTemplate.exchange(
                endpoint, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode responseJson = objectMapper.readTree(response.getBody());

                // FastAPI 응답 구조에 맞춰 점수 추출
                if (responseJson.has("score")) {
                    return new BigDecimal(responseJson.get("score").asText());
                } else if (responseJson.has("prediction")) {
                    return new BigDecimal(responseJson.get("prediction").asText());
                } else {
                    throw new RuntimeException("Invalid response format: no 'score' or 'prediction' field");
                }
            } else {
                throw new RuntimeException("Model API returned error: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Failed to call {} model API: {}", modelType, e.getMessage(), e);
            throw new RuntimeException("Failed to call " + modelType + " model: " + e.getMessage(), e);
        }
    }

    /**
     * 모델 타입 매핑 (내부 이름 -> FastAPI 엔드포인트 이름)
     */
    private String mapModelType(String modelType) {
        switch (modelType.toLowerCase()) {
            case "lgbm":
                return "lgbm";
            case "xgboost":
                return "xgboost";
            case "catboost":
                return "catboost";
            default:
                throw new IllegalArgumentException("Unknown model type: " + modelType);
        }
    }

    /**
     * 모델 서비스 헬스 체크
     */
    public boolean isModelServiceHealthy() {
        try {
            String healthEndpoint = modelServiceProperties.getUrl() + "/health";
            ResponseEntity<String> response = restTemplate.getForEntity(healthEndpoint, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode healthJson = objectMapper.readTree(response.getBody());
                return "healthy".equals(healthJson.get("status").asText());
            }
            return false;

        } catch (Exception e) {
            log.warn("Model service health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 현재 모델 버전 정보 조회 (GitHub Release 연동)
     */
    public Map<String, Object> getModelVersion() {
        return modelVersionService.getCurrentModelInfo();
    }

    /**
     * 사용 가능한 모든 모델 버전 목록 조회
     */
    public List<String> getAvailableVersions() {
        return modelVersionService.getAvailableVersions();
    }

    /**
     * 특정 버전으로 모델 업데이트
     */
    public boolean updateModelVersion(String version) {
        log.info("Updating model to version: {}", version);
        return modelVersionService.requestModelReload(version);
    }

    /**
     * 특정 버전의 메타데이터 조회
     */
    public Map<String, Object> getModelMetadata(String version) {
        return modelVersionService.getModelMetadata(version);
    }

    /**
     * 모델 서비스에 모델 재로드 요청
     */
    public boolean reloadModels() {
        try {
            String reloadEndpoint = modelServiceProperties.getUrl() + "/model/reload";
            ResponseEntity<String> response = restTemplate.postForEntity(reloadEndpoint, null, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Model reload requested successfully");
                return true;
            }

        } catch (Exception e) {
            log.error("Failed to request model reload: {}", e.getMessage());
        }

        return false;
    }

    /**
     * 모델 API 요청 데이터 구성
     */
    private Map<String, Object> buildModelRequest(ModelPredictionRequest request) {
        Map<String, Object> data = new HashMap<>();

        // IEEE 기본 거래 정보
        data.put("transaction_id", request.getTransactionId());
        data.put("transaction_dt", request.getTransactionDT());
        data.put("transaction_amt", request.getAmount());

        // IEEE 상품 및 결제 정보
        data.put("product_cd", request.getProductCode());
        data.put("card1", request.getCard1());
        data.put("card2", request.getCard2());
        data.put("card3", request.getCard3());
        data.put("card4", request.getCard4());
        data.put("card5", request.getCard5());
        data.put("card6", request.getCard6());

        // IEEE 주소 및 거리 정보
        data.put("addr1", request.getAddr1());
        data.put("addr2", request.getAddr2());
        data.put("dist1", request.getDist1());
        data.put("dist2", request.getDist2());

        // IEEE 이메일 도메인
        data.put("p_emaildomain", request.getPurchaserEmailDomain());
        data.put("r_emaildomain", request.getRecipientEmailDomain());

        // IEEE 피처 맵들
        data.put("counting_features", request.getCountingFeatures());
        data.put("time_deltas", request.getTimeDeltas());
        data.put("match_features", request.getMatchFeatures());
        data.put("vesta_features", request.getVestaFeatures());
        data.put("identity_features", request.getIdentityFeatures());

        // IEEE 디바이스 정보
        data.put("device_type", request.getDeviceType());
        data.put("device_info", request.getDeviceInfo());

        // 백엔드 생성 필드
        data.put("user_id", request.getUserId());
        data.put("merchant", request.getMerchant());
        data.put("merchant_category", request.getMerchantCategory());
        data.put("transaction_time", request.getTransactionTime());
        data.put("latitude", request.getLatitude());
        data.put("longitude", request.getLongitude());
        data.put("device_fingerprint", request.getDeviceFingerprint());
        data.put("ip_address", request.getIpAddress());

        return data;
    }

    /**
     * 가중치를 적용하여 최종 점수 계산
     */
    private BigDecimal calculateWeightedScore(BigDecimal lgbmScore, BigDecimal xgboostScore,
                                             BigDecimal catboostScore, Map<String, BigDecimal> weights) {
        BigDecimal lgbmWeight = weights.getOrDefault("lgbm", new BigDecimal("0.333"));
        BigDecimal xgboostWeight = weights.getOrDefault("xgboost", new BigDecimal("0.333"));
        BigDecimal catboostWeight = weights.getOrDefault("catboost", new BigDecimal("0.334"));

        return lgbmScore.multiply(lgbmWeight)
                .add(xgboostScore.multiply(xgboostWeight))
                .add(catboostScore.multiply(catboostWeight));
    }
}
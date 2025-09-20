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
    private final ExecutorService executorService = Executors.newFixedThreadPool(3);
    private final ModelServiceProperties modelServiceProperties;

    public ModelServiceClient(RestTemplate restTemplate, ObjectMapper objectMapper,
                              ModelServiceProperties modelServiceProperties) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.modelServiceProperties = modelServiceProperties;
    }

    /**
     * 앙상블 모델 예측 (3개 모델 개별 호출 후 가중치 적용)
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

            // 결과 대기
            BigDecimal lgbmScore = lgbmFuture.get();
            BigDecimal xgboostScore = xgboostFuture.get();
            BigDecimal catboostScore = catboostFuture.get();

            long processingTime = System.currentTimeMillis() - startTime;

            // 가중치 적용하여 최종 점수 계산
            Map<String, BigDecimal> weights = request.getModelWeights();
            if (weights == null) {
                weights = getDefaultWeights();
            }

            return ModelPredictionResponse.success(
                    request.getTransactionId(),
                    lgbmScore,
                    xgboostScore,
                    catboostScore,
                    weights.get("lgbm"),
                    weights.get("xgboost"),
                    weights.get("catboost"),
                    request.getThreshold() != null ? request.getThreshold() : new BigDecimal("0.5"),
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
            String endpoint = String.format("%s/model/%s/predict",
                    modelServiceProperties.getUrl(), modelType);

            // FastAPI 요청 구조에 맞게 데이터 구성
            Map<String, Object> requestData = new HashMap<>();
            requestData.put("payload", buildPayload(request));
            requestData.put("explain", false);
            requestData.put("top_n", 5);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestData, headers);

            log.debug("Calling {} model API: {}", modelType, endpoint);

            ResponseEntity<String> response = restTemplate.exchange(
                    endpoint, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode responseJson = objectMapper.readTree(response.getBody());

                // FastAPI 응답에서 fraud_probability 추출
                if (responseJson.has("fraud_probability")) {
                    return new BigDecimal(responseJson.get("fraud_probability").asText());
                } else {
                    throw new RuntimeException("Invalid response format: no 'fraud_probability' field");
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
     * 모델 서비스 헬스 체크
     */
    public boolean isModelServiceHealthy() {
        try {
            String healthEndpoint = modelServiceProperties.getUrl() + "/health";
            ResponseEntity<String> response = restTemplate.getForEntity(healthEndpoint, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode healthJson = objectMapper.readTree(response.getBody());
                return "ok".equals(healthJson.get("status").asText());
            }
            return false;

        } catch (Exception e) {
            log.warn("Model service health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 모델 정보 조회
     */
    public Map<String, Object> getModelVersion() {
        try {
            // GitHub Release에서 실제 버전 조회
            String latestVersion = getLatestReleaseVersion();

            // FastAPI에서 모델 상태 조회
            String endpoint = modelServiceProperties.getUrl() + "/models/info";
            ResponseEntity<String> response = restTemplate.getForEntity(endpoint, String.class);

            Map<String, Object> modelInfo = new HashMap<>();
            modelInfo.put("version", latestVersion); // 실제 릴리즈 버전

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode infoJson = objectMapper.readTree(response.getBody());

                modelInfo.put("lgbm_available", infoJson.path("lgbm").path("available").asBoolean());
                modelInfo.put("xgboost_available", infoJson.path("xgboost").path("available").asBoolean());
                modelInfo.put("catboost_available", infoJson.path("catboost").path("available").asBoolean());

                // 디렉터리 버전은 별도 필드로 저장
                modelInfo.put("lgbm_dir_version", infoJson.path("lgbm").path("version").asText("v5"));
                modelInfo.put("xgboost_dir_version", infoJson.path("xgboost").path("version").asText("v6"));
                modelInfo.put("catboost_dir_version", infoJson.path("catboost").path("version").asText("v7"));
            } else {
                // FastAPI 연결 실패시에도 릴리즈 버전은 반환
                modelInfo.put("lgbm_available", false);
                modelInfo.put("xgboost_available", false);
                modelInfo.put("catboost_available", false);
            }

            return modelInfo;

        } catch (Exception e) {
            log.error("Failed to get model info: {}", e.getMessage());
        }

        return Map.of("version", "unknown", "error", "Failed to retrieve model information");
    }

    /**
     * GitHub Release에서 최신 버전 조회
     */
    public String getLatestReleaseVersion() {
        try {
            String githubApiUrl = "https://api.github.com/repos/sookmyung-final-project-2025-1/Data/releases/latest";

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/vnd.github.v3+json");
            headers.set("User-Agent", "fraud-detection-backend");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    githubApiUrl, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode release = objectMapper.readTree(response.getBody());
                return release.get("tag_name").asText();
            }

        } catch (Exception e) {
            log.warn("Failed to fetch latest release version: {}", e.getMessage());
        }

        return "unknown";
    }

    /**
     * FastAPI payload 구성 (기존 FraudModel 형식에 맞게)
     */
    private Map<String, Object> buildPayload(ModelPredictionRequest request) {
        Map<String, Object> payload = new HashMap<>();

        // IEEE 기본 필드들만 포함
        if (request.getAmount() != null) {
            payload.put("TransactionAmt", request.getAmount());
        }
        if (request.getProductCode() != null) {
            payload.put("ProductCD", request.getProductCode());
        }
        if (request.getCard1() != null) {
            payload.put("card1", request.getCard1());
        }
        if (request.getCard2() != null) {
            payload.put("card2", request.getCard2());
        }
        if (request.getCard3() != null) {
            payload.put("card3", request.getCard3());
        }
        if (request.getAddr1() != null) {
            payload.put("addr1", request.getAddr1());
        }
        if (request.getAddr2() != null) {
            payload.put("addr2", request.getAddr2());
        }
        if (request.getDist1() != null) {
            payload.put("dist1", request.getDist1());
        }
        if (request.getPurchaserEmailDomain() != null) {
            payload.put("P_emaildomain", request.getPurchaserEmailDomain());
        }

        // 피처 맵들을 개별 필드로 전개
        if (request.getCountingFeatures() != null) {
            payload.putAll(request.getCountingFeatures());
        }
        if (request.getTimeDeltas() != null) {
            payload.putAll(request.getTimeDeltas());
        }
        if (request.getMatchFeatures() != null) {
            payload.putAll(request.getMatchFeatures());
        }
        if (request.getVestaFeatures() != null) {
            payload.putAll(request.getVestaFeatures());
        }
        if (request.getIdentityFeatures() != null) {
            payload.putAll(request.getIdentityFeatures());
        }

        return payload;
    }

    /**
     * 기본 가중치 반환
     */
    private Map<String, BigDecimal> getDefaultWeights() {
        Map<String, BigDecimal> weights = new HashMap<>();
        weights.put("lgbm", new BigDecimal("0.333"));
        weights.put("xgboost", new BigDecimal("0.333"));
        weights.put("catboost", new BigDecimal("0.334"));
        return weights;
    }

    /**
     * 모델 재로드 (스텁 구현)
     */
    public boolean reloadModels() {
        log.info("Model reload requested (not implemented for single model setup)");
        return true;
    }

    /**
     * 사용 가능한 버전 목록 (스텁 구현)
     */
    public List<String> getAvailableVersions() {
        return List.of(getLatestReleaseVersion());
    }

    /**
     * 모델 버전 업데이트 (스텁 구현)
     */
    public boolean updateModelVersion(String version) {
        log.info("Model version update requested to {} (not implemented for single model setup)", version);
        return true;
    }

    /**
     * 모델 메타데이터 조회 (스텁 구현)
     */
    public Map<String, Object> getModelMetadata(String version) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("version", version);
        metadata.put("note", "Metadata not available for single model setup");
        return metadata;
    }
}
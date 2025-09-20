package com.credit.card.fraud.detection.modelclient.service;

import com.credit.card.fraud.detection.modelclient.dto.ModelPredictionRequest;
import com.credit.card.fraud.detection.modelclient.dto.ModelPredictionResponse;
import com.credit.card.fraud.detection.modelclient.dto.ModelWeightsUpdateRequest;
import com.credit.card.fraud.detection.global.config.ModelServiceProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnsembleModelService {

    private final AtomicReference<BigDecimal> lgbmWeight = new AtomicReference<>(new BigDecimal("0.333"));
    private final AtomicReference<BigDecimal> xgboostWeight = new AtomicReference<>(new BigDecimal("0.333"));
    private final AtomicReference<BigDecimal> catboostWeight = new AtomicReference<>(new BigDecimal("0.334"));
    private final AtomicReference<BigDecimal> threshold = new AtomicReference<>(new BigDecimal("0.5"));

    private final ModelServiceClient modelServiceClient;
    private final ModelServiceProperties modelServiceProperties;
    private final Random random = new Random();

    public ModelPredictionResponse predict(ModelPredictionRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            // 현재 가중치 설정
            Map<String, BigDecimal> currentWeights = getCurrentWeights();
            request.setModelWeights(currentWeights);
            request.setThreshold(threshold.get());

            // 실제 모델 서비스 사용 여부 확인
            if (modelServiceProperties.isEnabled() && modelServiceClient.isModelServiceHealthy()) {
                log.debug("Using real model service for transaction {}", request.getTransactionId());

                ModelPredictionResponse response = modelServiceClient.predictEnsemble(request);

                // 동적으로 최신 버전 설정
                Map<String, Object> versionInfo = modelServiceClient.getModelVersion();
                String latestVersion = versionInfo.getOrDefault("version", "unknown").toString();
                response.setModelVersion(latestVersion);

                log.debug("Real model prediction completed for transaction {}: finalScore={}, prediction={}",
                        request.getTransactionId(), response.getFinalScore(), response.getFinalPrediction());

                return response;

            } else {
                log.warn("Model service not available, using simulation for transaction {}", request.getTransactionId());
                return predictWithSimulation(request, startTime);
            }

        } catch (Exception e) {
            log.error("Error during prediction for transaction {}: {}, falling back to simulation",
                    request.getTransactionId(), e.getMessage());
            return predictWithSimulation(request, startTime);
        }
    }

    public ModelPredictionResponse predictWithCustomWeights(
            ModelPredictionRequest request,
            BigDecimal lgbmWeight,
            BigDecimal xgboostWeight,
            BigDecimal catboostWeight) {

        // 가중치 정규화
        BigDecimal totalWeight = lgbmWeight.add(xgboostWeight).add(catboostWeight);
        if (totalWeight.compareTo(BigDecimal.ONE) != 0) {
            lgbmWeight = lgbmWeight.divide(totalWeight, 6, RoundingMode.HALF_UP);
            xgboostWeight = xgboostWeight.divide(totalWeight, 6, RoundingMode.HALF_UP);
            catboostWeight = catboostWeight.divide(totalWeight, 6, RoundingMode.HALF_UP);
        }

        // 사용자 정의 가중치 설정
        Map<String, BigDecimal> customWeights = new HashMap<>();
        customWeights.put("lgbm", lgbmWeight);
        customWeights.put("xgboost", xgboostWeight);
        customWeights.put("catboost", catboostWeight);

        request.setModelWeights(customWeights);
        request.setThreshold(threshold.get());

        long startTime = System.currentTimeMillis();

        try {
            if (modelServiceProperties.isEnabled() && modelServiceClient.isModelServiceHealthy()) {
                ModelPredictionResponse response = modelServiceClient.predictEnsemble(request);

                // 동적으로 최신 버전 설정
                Map<String, Object> versionInfo = modelServiceClient.getModelVersion();
                String latestVersion = versionInfo.getOrDefault("version", "unknown").toString();
                response.setModelVersion(latestVersion);

                return response;
            } else {
                return predictWithSimulation(request, startTime);
            }
        } catch (Exception e) {
            log.error("Error during custom weight prediction for transaction {}: {}, falling back to simulation",
                    request.getTransactionId(), e.getMessage());
            return predictWithSimulation(request, startTime);
        }
    }

    /**
     * 시뮬레이션 예측 (폴백)
     */
    private ModelPredictionResponse predictWithSimulation(ModelPredictionRequest request, long startTime) {
        try {
            // 간단한 시뮬레이션 점수 생성
            BigDecimal lgbmScore = generateSimpleScore(request, 0.92);  // LGBM 보수적
            BigDecimal xgboostScore = generateSimpleScore(request, 1.08);  // XGBoost 공격적
            BigDecimal catboostScore = generateSimpleScore(request, 1.0);   // CatBoost 균형

            Map<String, BigDecimal> weights = request.getModelWeights();
            if (weights == null) {
                weights = getCurrentWeights();
            }

            long processingTime = System.currentTimeMillis() - startTime;

            ModelPredictionResponse response = ModelPredictionResponse.success(
                    request.getTransactionId(),
                    lgbmScore,
                    xgboostScore,
                    catboostScore,
                    weights.get("lgbm"),
                    weights.get("xgboost"),
                    weights.get("catboost"),
                    threshold.get(),
                    processingTime
            );

            response.setModelVersion(getLatestAvailableVersion());
            return response;

        } catch (Exception e) {
            log.error("Simulation prediction failed for transaction {}: {}", request.getTransactionId(), e.getMessage());
            return ModelPredictionResponse.error(request.getTransactionId(), e.getMessage());
        }
    }

    /**
     * 간단한 점수 생성 (주요 피처만 사용)
     */
    private BigDecimal generateSimpleScore(ModelPredictionRequest request, double modelMultiplier) {
        double baseScore = random.nextDouble() * 0.3; // 기본 점수

        // 거래 금액 기반 위험도
        if (request.getAmount() != null) {
            if (request.getAmount().compareTo(new BigDecimal("1000")) > 0) {
                baseScore += 0.15;
            }
            if (request.getAmount().compareTo(new BigDecimal("5000")) > 0) {
                baseScore += 0.2;
            }
        }

        // 상품 코드 기반 위험도
        if (request.getProductCode() != null) {
            switch (request.getProductCode()) {
                case "W": baseScore += 0.25; break;
                case "C": baseScore += 0.1; break;
                case "H": baseScore += 0.05; break;
                default: baseScore += 0.08;
            }
        }

        // 모델별 특성 반영
        baseScore *= modelMultiplier;

        return BigDecimal.valueOf(Math.min(0.999, Math.max(0.001, baseScore)))
                .setScale(6, RoundingMode.HALF_UP);
    }

    public void updateWeights(ModelWeightsUpdateRequest request) {
        lgbmWeight.set(request.getLgbmWeight());
        xgboostWeight.set(request.getXgboostWeight());
        catboostWeight.set(request.getCatboostWeight());

        log.info("Model weights updated: LGBM={}, XGBoost={}, CatBoost={}",
                request.getLgbmWeight(), request.getXgboostWeight(), request.getCatboostWeight());
    }

    public void updateThreshold(BigDecimal newThreshold) {
        threshold.set(newThreshold);
        log.info("Prediction threshold updated to: {}", newThreshold);
    }

    public Map<String, BigDecimal> getCurrentWeights() {
        Map<String, BigDecimal> weights = new HashMap<>();
        weights.put("lgbm", lgbmWeight.get());
        weights.put("xgboost", xgboostWeight.get());
        weights.put("catboost", catboostWeight.get());
        return weights;
    }

    public BigDecimal getCurrentThreshold() {
        return threshold.get();
    }

    /**
     * 최신 사용 가능한 버전 조회
     */
    private String getLatestAvailableVersion() {
        try {
            // 실제 모델 서비스에서 버전 정보 조회 시도
            Map<String, Object> versionInfo = modelServiceClient.getModelVersion();
            String version = versionInfo.getOrDefault("version", "").toString();

            if (!"unknown".equals(version) && !version.isEmpty()) {
                return version + "-simulation";
            }

            // 폴백: GitHub Release에서 최신 버전 조회
            String latestRelease = modelServiceClient.getLatestReleaseVersion();
            if (!"unknown".equals(latestRelease)) {
                return latestRelease + "-simulation";
            }

        } catch (Exception e) {
            log.warn("Failed to get latest version: {}", e.getMessage());
        }

        return "v1.2.0-simulation"; // 최종 폴백
    }
}
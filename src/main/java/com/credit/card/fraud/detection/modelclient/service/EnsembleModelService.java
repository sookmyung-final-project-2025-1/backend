package com.credit.card.fraud.detection.modelclient.service;

import com.credit.card.fraud.detection.modelclient.dto.ModelPredictionRequest;
import com.credit.card.fraud.detection.modelclient.dto.ModelPredictionResponse;
import com.credit.card.fraud.detection.modelclient.dto.ModelWeightsUpdateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
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
    
    private final Random random = new Random();

    public ModelPredictionResponse predict(ModelPredictionRequest request) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 시뮬레이션된 개별 모델 점수 생성
            BigDecimal lgbmScore = generateModelScore(request, "lgbm");
            BigDecimal xgboostScore = generateModelScore(request, "xgboost");
            BigDecimal catboostScore = generateModelScore(request, "catboost");
            
            // 현재 가중치 가져오기
            BigDecimal currentLgbmWeight = lgbmWeight.get();
            BigDecimal currentXgboostWeight = xgboostWeight.get();
            BigDecimal currentCatboostWeight = catboostWeight.get();
            BigDecimal currentThreshold = threshold.get();
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            ModelPredictionResponse response = ModelPredictionResponse.success(
                request.getTransactionId(),
                lgbmScore,
                xgboostScore,
                catboostScore,
                currentLgbmWeight,
                currentXgboostWeight,
                currentCatboostWeight,
                currentThreshold,
                processingTime
            );
            
            // 피처 중요도 시뮬레이션 추가
            response.setFeatureImportance(generateFeatureImportance());
            response.setConfidenceScore(calculateConfidenceScore(response.getFinalScore()));
            response.setModelVersion("v1.0.0");
            
            log.debug("Prediction completed for transaction {}: finalScore={}, prediction={}", 
                request.getTransactionId(), response.getFinalScore(), response.getFinalPrediction());
                
            return response;
            
        } catch (Exception e) {
            log.error("Error during prediction for transaction {}: {}", request.getTransactionId(), e.getMessage());
            return ModelPredictionResponse.error(request.getTransactionId(), e.getMessage());
        }
    }

    private BigDecimal generateModelScore(ModelPredictionRequest request, String modelType) {
        // 실제 환경에서는 여기서 각 모델 API를 호출
        // 현재는 시뮬레이션된 점수 생성
        
        double baseScore = random.nextDouble();
        
        // 금액이 클수록 더 높은 사기 확률
        if (request.getAmount().compareTo(new BigDecimal("10000")) > 0) {
            baseScore += 0.2;
        }
        
        // 심야 시간대 거래는 더 위험
        int hour = request.getTransactionTime().getHour();
        if (hour >= 23 || hour <= 5) {
            baseScore += 0.15;
        }
        
        // 특정 merchant category는 더 위험
        if (request.getMerchantCategory() != null && 
            (request.getMerchantCategory().contains("ONLINE") || 
             request.getMerchantCategory().contains("ATM"))) {
            baseScore += 0.1;
        }
        
        // 모델별 특성 반영
        switch (modelType) {
            case "lgbm":
                baseScore *= 0.95; // LGBM이 조금 더 보수적
                break;
            case "xgboost":
                baseScore *= 1.05; // XGBoost가 조금 더 공격적
                break;
            case "catboost":
                baseScore *= 1.0; // CatBoost는 중간
                break;
        }
        
        return BigDecimal.valueOf(Math.min(0.999, Math.max(0.001, baseScore)))
                        .setScale(6, RoundingMode.HALF_UP);
    }

    private Map<String, BigDecimal> generateFeatureImportance() {
        Map<String, BigDecimal> importance = new HashMap<>();
        
        // 익명화된 피처들의 중요도 시뮬레이션
        String[] features = {"C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10",
                           "C11", "C12", "C13", "C14", "amount", "hour", "merchant_category"};
        
        for (String feature : features) {
            importance.put(feature, BigDecimal.valueOf(random.nextDouble())
                                             .setScale(4, RoundingMode.HALF_UP));
        }
        
        return importance;
    }

    private BigDecimal calculateConfidenceScore(BigDecimal finalScore) {
        // Confidence score는 예측의 확실성을 나타냄
        // 0.5에서 멀어질수록 더 높은 confidence
        BigDecimal distance = finalScore.subtract(new BigDecimal("0.5")).abs();
        BigDecimal confidence = distance.multiply(new BigDecimal("2")); // 0~1 범위로 정규화
        
        // 약간의 노이즈 추가 (실제 모델의 불확실성 반영)
        BigDecimal noise = BigDecimal.valueOf(random.nextGaussian() * 0.05);
        confidence = confidence.add(noise);
        
        // 0~1 범위로 클리핑
        confidence = confidence.max(BigDecimal.ZERO).min(BigDecimal.ONE);
        
        return confidence.setScale(6, RoundingMode.HALF_UP);
    }

    public void updateWeights(ModelWeightsUpdateRequest request) {
        if (request.getAutoNormalize() && !request.isValidWeightSum()) {
            request.normalizeWeights();
        }
        
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
}
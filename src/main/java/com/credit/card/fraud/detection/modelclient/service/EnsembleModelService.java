package com.credit.card.fraud.detection.modelclient.service;

import com.credit.card.fraud.detection.modelclient.dto.ModelPredictionRequest;
import com.credit.card.fraud.detection.modelclient.dto.ModelPredictionResponse;
import com.credit.card.fraud.detection.modelclient.dto.ModelWeightsUpdateRequest;
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
        // IEEE 데이터셋 기반 시뮬레이션된 점수 생성

        double baseScore = random.nextDouble() * 0.5; // 기본 점수를 낮게 시작

        // IEEE TransactionAMT 기반 위험도 평가
        if (request.getAmount() != null) {
            // 고액 거래 위험도
            if (request.getAmount().compareTo(new BigDecimal("1000")) > 0) {
                baseScore += 0.15;
            }
            if (request.getAmount().compareTo(new BigDecimal("5000")) > 0) {
                baseScore += 0.2;
            }
        }

        // IEEE ProductCD 기반 위험도 평가
        if (request.getProductCode() != null) {
            switch (request.getProductCode()) {
                case "W": // 가장 위험한 상품 코드
                    baseScore += 0.25;
                    break;
                case "C": // 중간 위험
                    baseScore += 0.1;
                    break;
                case "H": // 낮은 위험
                    baseScore += 0.05;
                    break;
                default:
                    baseScore += 0.08;
            }
        }

        // IEEE 이메일 도메인 기반 위험도 평가
        if (request.getPurchaserEmailDomain() != null) {
            // 임시 이메일 서비스는 위험
            if (request.getPurchaserEmailDomain().contains("tempmail") ||
                request.getPurchaserEmailDomain().contains("guerrillamail")) {
                baseScore += 0.3;
            }
            // 유명 도메인은 안전
            else if (request.getPurchaserEmailDomain().contains("gmail") ||
                     request.getPurchaserEmailDomain().contains("yahoo")) {
                baseScore -= 0.1;
            }
        }

        // IEEE 카드 정보 기반 위험도 평가
        if (request.getCard1() != null && request.getCard1().length() > 0) {
            // 특정 카드 번호 패턴 위험도
            int cardNum = Integer.parseInt(request.getCard1().substring(0, Math.min(4, request.getCard1().length())));
            if (cardNum < 2000) {
                baseScore += 0.1;
            }
        }

        // IEEE 시간 델타 피처 기반 평가
        if (request.getTimeDeltas() != null && !request.getTimeDeltas().isEmpty()) {
            // D1 (이전 거래와의 시간 차이)이 작으면 위험
            BigDecimal d1 = request.getTimeDeltas().get("D1");
            if (d1 != null && d1.compareTo(new BigDecimal("1")) < 0) {
                baseScore += 0.2; // 짧은 시간 간격은 위험
            }
        }

        // IEEE 카운팅 피처 기반 평가
        if (request.getCountingFeatures() != null && !request.getCountingFeatures().isEmpty()) {
            // C1~C14 중 일부 피처들의 높은 값은 위험 신호
            for (int i = 1; i <= 5; i++) {
                BigDecimal c = request.getCountingFeatures().get("C" + i);
                if (c != null && c.compareTo(new BigDecimal("10")) > 0) {
                    baseScore += 0.05;
                }
            }
        }

        // IEEE 거리 정보 기반 평가
        if (request.getDist1() != null && request.getDist1().compareTo(new BigDecimal("1000")) > 0) {
            baseScore += 0.15; // 먼 거리 거래는 위험
        }

        // IEEE Vesta 피처 기반 평가 (V1~V339 중 주요 피처들)
        if (request.getVestaFeatures() != null && !request.getVestaFeatures().isEmpty()) {
            // V1~V11은 일반적으로 중요한 피처들
            for (int i = 1; i <= 11; i++) {
                BigDecimal v = request.getVestaFeatures().get("V" + i);
                if (v != null && v.compareTo(new BigDecimal("1")) > 0) {
                    baseScore += 0.02;
                }
            }
        }

        // IEEE Identity 피처 기반 평가
        if (request.getIdentityFeatures() != null && !request.getIdentityFeatures().isEmpty()) {
            // id_01~id_11은 중요한 신원 정보
            for (int i = 1; i <= 11; i++) {
                BigDecimal id = request.getIdentityFeatures().get("id_" + String.format("%02d", i));
                if (id != null && id.compareTo(BigDecimal.ZERO) > 0) {
                    baseScore += 0.01;
                }
            }
        }

        // 디바이스 정보 기반 평가
        if (request.getDeviceType() != null) {
            if (request.getDeviceType().equals("mobile")) {
                baseScore += 0.05; // 모바일은 약간 더 위험
            } else if (request.getDeviceType().equals("desktop")) {
                baseScore -= 0.05; // 데스크톱은 약간 더 안전
            }
        }

        // 매치 피처 기반 평가 (M1~M9)
        if (request.getMatchFeatures() != null && !request.getMatchFeatures().isEmpty()) {
            long matchCount = request.getMatchFeatures().values().stream()
                .mapToLong(value -> "T".equals(value) ? 1 : 0)
                .sum();

            if (matchCount < 3) {
                baseScore += 0.2; // 매치가 적으면 위험
            } else if (matchCount > 7) {
                baseScore -= 0.1; // 매치가 많으면 안전
            }
        }

        // 모델별 특성 반영 (IEEE 대회 결과 기반)
        switch (modelType) {
            case "lgbm":
                baseScore *= 0.92; // LGBM이 보수적, 정밀도 높음
                break;
            case "xgboost":
                baseScore *= 1.08; // XGBoost가 공격적, 재현율 높음
                break;
            case "catboost":
                baseScore *= 1.0;  // CatBoost는 균형잡힌 성능
                break;
        }

        return BigDecimal.valueOf(Math.min(0.999, Math.max(0.001, baseScore)))
                        .setScale(6, RoundingMode.HALF_UP);
    }

    private Map<String, BigDecimal> generateFeatureImportance() {
        Map<String, BigDecimal> importance = new HashMap<>();

        // IEEE 데이터셋 기반 실제 중요 피처들 시뮬레이션
        // IEEE-CIS 대회 결과 기반 주요 피처들

        // 거래 기본 정보 (높은 중요도)
        importance.put("TransactionAMT", BigDecimal.valueOf(0.15 + random.nextDouble() * 0.1).setScale(4, RoundingMode.HALF_UP));
        importance.put("ProductCD", BigDecimal.valueOf(0.12 + random.nextDouble() * 0.08).setScale(4, RoundingMode.HALF_UP));

        // 카드 정보 (중요도 높음)
        importance.put("card1", BigDecimal.valueOf(0.10 + random.nextDouble() * 0.08).setScale(4, RoundingMode.HALF_UP));
        importance.put("card2", BigDecimal.valueOf(0.08 + random.nextDouble() * 0.06).setScale(4, RoundingMode.HALF_UP));
        importance.put("card3", BigDecimal.valueOf(0.06 + random.nextDouble() * 0.04).setScale(4, RoundingMode.HALF_UP));
        importance.put("card5", BigDecimal.valueOf(0.05 + random.nextDouble() * 0.04).setScale(4, RoundingMode.HALF_UP));

        // 주소 및 거리 정보
        importance.put("addr1", BigDecimal.valueOf(0.07 + random.nextDouble() * 0.05).setScale(4, RoundingMode.HALF_UP));
        importance.put("addr2", BigDecimal.valueOf(0.04 + random.nextDouble() * 0.03).setScale(4, RoundingMode.HALF_UP));
        importance.put("dist1", BigDecimal.valueOf(0.06 + random.nextDouble() * 0.04).setScale(4, RoundingMode.HALF_UP));
        importance.put("dist2", BigDecimal.valueOf(0.05 + random.nextDouble() * 0.04).setScale(4, RoundingMode.HALF_UP));

        // 이메일 도메인
        importance.put("P_emaildomain", BigDecimal.valueOf(0.08 + random.nextDouble() * 0.05).setScale(4, RoundingMode.HALF_UP));
        importance.put("R_emaildomain", BigDecimal.valueOf(0.03 + random.nextDouble() * 0.02).setScale(4, RoundingMode.HALF_UP));

        // 카운팅 피처 (C1-C14) - 일부만 중요
        String[] importantC = {"C1", "C2", "C4", "C5", "C6", "C8", "C9", "C11", "C13", "C14"};
        for (String c : importantC) {
            importance.put(c, BigDecimal.valueOf(0.02 + random.nextDouble() * 0.04).setScale(4, RoundingMode.HALF_UP));
        }

        // 시간 델타 피처 (D1-D15) - 주요한 것들
        String[] importantD = {"D1", "D2", "D3", "D4", "D8", "D9", "D10", "D11", "D15"};
        for (String d : importantD) {
            importance.put(d, BigDecimal.valueOf(0.01 + random.nextDouble() * 0.03).setScale(4, RoundingMode.HALF_UP));
        }

        // 매치 피처 (M1-M9) - 모두 중요
        for (int i = 1; i <= 9; i++) {
            importance.put("M" + i, BigDecimal.valueOf(0.02 + random.nextDouble() * 0.03).setScale(4, RoundingMode.HALF_UP));
        }

        // Vesta 피처 (V1-V339) - 주요한 것들만
        String[] importantV = {"V1", "V2", "V3", "V4", "V6", "V8", "V11", "V12", "V13", "V17",
                              "V19", "V20", "V29", "V30", "V33", "V34", "V35", "V36", "V37", "V38"};
        for (String v : importantV) {
            importance.put(v, BigDecimal.valueOf(0.01 + random.nextDouble() * 0.02).setScale(4, RoundingMode.HALF_UP));
        }

        // Identity 피처 (id_01-id_38) - 주요한 것들
        String[] importantId = {"id_01", "id_02", "id_03", "id_05", "id_06", "id_09", "id_11",
                               "id_12", "id_13", "id_14", "id_15", "id_17", "id_19", "id_20"};
        for (String id : importantId) {
            importance.put(id, BigDecimal.valueOf(0.005 + random.nextDouble() * 0.015).setScale(4, RoundingMode.HALF_UP));
        }

        // 디바이스 정보
        importance.put("DeviceType", BigDecimal.valueOf(0.03 + random.nextDouble() * 0.02).setScale(4, RoundingMode.HALF_UP));
        importance.put("DeviceInfo", BigDecimal.valueOf(0.02 + random.nextDouble() * 0.015).setScale(4, RoundingMode.HALF_UP));

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
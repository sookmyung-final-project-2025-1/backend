package com.credit.card.fraud.detection.modelclient.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModelPredictionRequest {

    // IEEE 기본 거래 정보
    private Long transactionId;
    private BigDecimal transactionDT;  // IEEE: TransactionDT (timedelta)
    private BigDecimal amount;         // IEEE: TransactionAMT

    // IEEE 상품 및 결제 정보
    private String productCode;        // IEEE: ProductCD
    private String card1;              // IEEE: card1 (카드 정보)
    private String card2;              // IEEE: card2
    private String card3;              // IEEE: card3
    private BigDecimal card4;          // IEEE: card4
    private String card5;              // IEEE: card5
    private BigDecimal card6;          // IEEE: card6

    // IEEE 주소 및 거리 정보
    private BigDecimal addr1;          // IEEE: addr1
    private BigDecimal addr2;          // IEEE: addr2
    private BigDecimal dist1;          // IEEE: dist1
    private BigDecimal dist2;          // IEEE: dist2

    // IEEE 이메일 도메인
    private String purchaserEmailDomain;   // IEEE: P_emaildomain
    private String recipientEmailDomain;   // IEEE: R_emaildomain

    // IEEE 카운팅 피처 (C1-C14)
    private Map<String, BigDecimal> countingFeatures;  // IEEE: C1-C14

    // IEEE 시간 델타 피처 (D1-D15)
    private Map<String, BigDecimal> timeDeltas;        // IEEE: D1-D15

    // IEEE 매치 피처 (M1-M9)
    private Map<String, String> matchFeatures;         // IEEE: M1-M9

    // IEEE Vesta 엔지니어링 피처 (V1-V339)
    private Map<String, BigDecimal> vestaFeatures;     // IEEE: V1-V339

    // IEEE Identity 피처 (id_01-id_38)
    private Map<String, BigDecimal> identityFeatures;  // IEEE: id_01-id_38

    // IEEE 디바이스 정보
    private String deviceType;         // IEEE: DeviceType
    private String deviceInfo;         // IEEE: DeviceInfo

    // 백엔드 생성 필드 (IEEE에서 파생)
    private String userId;             // 생성: TransactionID 기반
    private String merchant;           // 생성: ProductCD 기반
    private String merchantCategory;   // 생성: ProductCD 기반
    private LocalDateTime transactionTime;  // 생성: TransactionDT 변환
    private BigDecimal latitude;       // 생성: addr1/addr2 기반 추정
    private BigDecimal longitude;      // 생성: addr1/addr2 기반 추정
    private String deviceFingerprint;  // 생성: DeviceType+DeviceInfo 조합
    private String ipAddress;          // 생성: identity features 기반

    // 호환성을 위한 기존 필드 (삭제 예정)
    @Deprecated
    private Map<String, Object> anonymizedFeatures;

    // 모델 설정
    private Map<String, BigDecimal> modelWeights;
    private BigDecimal threshold;
    private String modelVersion;
}
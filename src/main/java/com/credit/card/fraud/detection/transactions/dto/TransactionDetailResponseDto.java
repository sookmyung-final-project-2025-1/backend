package com.credit.card.fraud.detection.transactions.dto;

import com.credit.card.fraud.detection.transactions.entity.Transaction;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "거래 상세 응답 DTO (연관관계 포함)")
public class TransactionDetailResponseDto {

    // 기본 거래 정보
    @Schema(description = "거래 ID", example = "12345")
    private Long id;

    @Schema(description = "사용자 ID", example = "USER_12345")
    private String userId;

    @Schema(description = "거래 금액", example = "150000.00")
    private BigDecimal amount;

    @Schema(description = "가맹점명", example = "STARBUCKS_GANGNAM")
    private String merchant;

    @Schema(description = "가맹점 카테고리", example = "FOOD_BEVERAGE")
    private String merchantCategory;

    @Schema(description = "거래 시간", example = "2024-01-15T14:30:00")
    private LocalDateTime transactionTime;

    @Schema(description = "가상 시간", example = "2024-01-15T14:30:00")
    private LocalDateTime virtualTime;

    @Schema(description = "사기 여부", example = "false")
    private Boolean isFraud;

    @Schema(description = "골드 라벨 (관리자 검토 결과)", example = "null")
    private Boolean goldLabel;

    @Schema(description = "위도", example = "37.5665")
    private BigDecimal latitude;

    @Schema(description = "경도", example = "126.9780")
    private BigDecimal longitude;

    @Schema(description = "디바이스 지문", example = "DEVICE_ABC123")
    private String deviceFingerprint;

    @Schema(description = "IP 주소", example = "192.168.1.1")
    private String ipAddress;

    @Schema(description = "거래 상태", example = "PROCESSED")
    private Transaction.TransactionStatus status;

    @Schema(description = "외부 거래 ID", example = "EXT_TXN_123456")
    private String externalTransactionId;

    @Schema(description = "통화", example = "KRW")
    private String currency;

    @Schema(description = "마스킹된 카드 번호", example = "****1234")
    private String maskedCardNumber;

    @Schema(description = "생성 시간")
    private LocalDateTime createdAt;

    @Schema(description = "수정 시간")
    private LocalDateTime updatedAt;

    // 연관관계 정보
    @Schema(description = "사용자 신고 목록")
    private List<UserReportDto> reports;

    @Schema(description = "사기 탐지 결과 목록")
    private List<FraudDetectionResultDto> detectionResults;

    /**
     * Transaction 엔티티를 TransactionDetailResponseDto로 변환
     */
    public static TransactionDetailResponseDto from(Transaction transaction,
                                                     List<UserReportDto> reports,
                                                     List<FraudDetectionResultDto> detectionResults) {
        return TransactionDetailResponseDto.builder()
                .id(transaction.getId())
                .userId(transaction.getUserId())
                .amount(transaction.getAmount())
                .merchant(transaction.getMerchant())
                .merchantCategory(transaction.getMerchantCategory())
                .transactionTime(transaction.getTransactionTime())
                .virtualTime(transaction.getVirtualTime())
                .isFraud(transaction.getIsFraud())
                .goldLabel(transaction.getGoldLabel())
                .latitude(transaction.getLatitude())
                .longitude(transaction.getLongitude())
                .deviceFingerprint(transaction.getDeviceFingerprint())
                .ipAddress(transaction.getIpAddress())
                .status(transaction.getStatus())
                .externalTransactionId(transaction.getExternalTransactionId())
                .currency(transaction.getCurrency())
                .maskedCardNumber(transaction.getMaskedCardNumber())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .reports(reports)
                .detectionResults(detectionResults)
                .build();
    }
}
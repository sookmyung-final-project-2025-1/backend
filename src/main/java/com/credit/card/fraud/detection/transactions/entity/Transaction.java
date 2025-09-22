package com.credit.card.fraud.detection.transactions.entity;

import com.credit.card.fraud.detection.common.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_user_id", columnList = "userId"),
        @Index(name = "idx_merchant", columnList = "merchant"),
        @Index(name = "idx_transaction_time", columnList = "transactionTime"),
        @Index(name = "idx_is_fraud", columnList = "isFraud"),
        @Index(name = "idx_virtual_time", columnList = "virtualTime"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_gold_label", columnList = "goldLabel"),
        @Index(name = "idx_merchant_category", columnList = "merchantCategory")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "거래 정보")
public class Transaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 200)
    private String merchant;

    @Column(name = "merchant_category", nullable = false, length = 100)
    private String merchantCategory;

    @Column(name = "transaction_time", nullable = false)
    private LocalDateTime transactionTime;

    @Column(name = "virtual_time", nullable = false)
    private LocalDateTime virtualTime;

    @Column(name = "is_fraud", nullable = false)
    @Builder.Default
    private Boolean isFraud = false;

    @Column(name = "gold_label")
    private Boolean goldLabel; // true: fraud, false: legitimate, null: unknown

    @Column(name = "anonymized_features", columnDefinition = "TEXT")
    private String anonymizedFeatures;

    @Column(precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(precision = 11, scale = 8)
    private BigDecimal longitude;

    @Column(name = "device_fingerprint", length = 255)
    private String deviceFingerprint;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;

    @Column(name = "external_transaction_id", length = 100)
    private String externalTransactionId;

    @Column(length = 10)
    @Builder.Default
    private String currency = "KRW";

    @Column(name = "card_number", length = 20)
    private String cardNumber;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @BatchSize(size = 100)
    @Builder.Default
    private List<UserReport> reports = new ArrayList<>();

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Fetch(FetchMode.SUBSELECT)
    @BatchSize(size = 100)
    @Builder.Default
    private List<FraudDetectionResult> detectionResults = new ArrayList<>();

    public enum TransactionStatus {
        PENDING,    // 처리 대기
        PROCESSED,  // 처리 완료
        FAILED,     // 처리 실패
        BLOCKED,    // 차단됨
        ERROR       // 오류 발생
    }

    /**
     * 사기 거래로 표시
     */
    public void markAsFraud() {
        this.isFraud = true;
        if (this.status == TransactionStatus.PENDING) {
            this.status = TransactionStatus.BLOCKED;
        }
    }

    /**
     * 골드 라벨 설정 (관리자 검토 결과)
     */
    public void setGoldLabel(Boolean isFraud) {
        this.goldLabel = isFraud;
        if (isFraud != null) {
            this.isFraud = isFraud;
        }
    }

    /**
     * 거래 상태 업데이트
     */
    public void updateStatus(TransactionStatus status) {
        this.status = status;
    }

    /**
     * 거래 처리 완료
     */
    public void markAsProcessed() {
        this.status = TransactionStatus.PROCESSED;
    }

    /**
     * 거래 처리 실패
     */
    public void markAsFailed() {
        this.status = TransactionStatus.FAILED;
    }

    /**
     * 골드 라벨 존재 여부
     */
    public boolean hasGoldLabel() {
        return goldLabel != null;
    }

    /**
     * 사기 거래 여부 (골드 라벨 기준)
     */
    public boolean isFraudByGoldLabel() {
        return Boolean.TRUE.equals(goldLabel);
    }

    /**
     * 정상 거래 여부 (골드 라벨 기준)
     */
    public boolean isLegitimateByGoldLabel() {
        return Boolean.FALSE.equals(goldLabel);
    }

    /**
     * 고액 거래 여부
     */
    public boolean isHighValue() {
        return amount != null && amount.compareTo(new BigDecimal("1000000")) >= 0; // 100만원 이상
    }

    /**
     * 해외 거래 여부 (간단한 판별 로직)
     */
    public boolean isInternational() {
        return merchantCategory != null &&
                (merchantCategory.contains("INTERNATIONAL") || merchantCategory.contains("OVERSEAS"));
    }

    /**
     * 신고 추가
     */
    public void addReport(UserReport report) {
        reports.add(report);
        report.setTransaction(this);
    }

    /**
     * 탐지 결과 추가
     */
    public void addDetectionResult(FraudDetectionResult result) {
        detectionResults.add(result);
        result.setTransaction(this);
    }

    /**
     * 활성 신고 개수
     */
    public long getActiveReportsCount() {
        return reports.stream()
                .filter(UserReport::isPending)
                .count();
    }

    /**
     * 승인된 신고 여부
     */
    public boolean hasApprovedReports() {
        return reports.stream()
                .anyMatch(report -> report.getStatus() == UserReport.ReportStatus.APPROVED);
    }

    /**
     * 최신 탐지 결과 조회
     */
    public FraudDetectionResult getLatestDetectionResult() {
        return detectionResults.stream()
                .max((r1, r2) -> r1.getCreatedAt().compareTo(r2.getCreatedAt()))
                .orElse(null);
    }

    /**
     * 처리 가능한 상태인지 확인
     */
    public boolean isProcessable() {
        return status == TransactionStatus.PENDING;
    }

    /**
     * 완료된 상태인지 확인
     */
    public boolean isCompleted() {
        return status == TransactionStatus.PROCESSED || status == TransactionStatus.BLOCKED;
    }

    /**
     * 마스킹된 카드 번호 반환
     */
    public String getMaskedCardNumber() {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****";
        }
        int visibleLength = Math.min(4, cardNumber.length());
        String visible = cardNumber.substring(cardNumber.length() - visibleLength);
        return "*".repeat(cardNumber.length() - visibleLength) + visible;
    }
}
package com.credit.card.fraud.detection.transactions.entity;

import com.credit.card.fraud.detection.common.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_user_id", columnList = "userId"),
    @Index(name = "idx_merchant", columnList = "merchant"),
    @Index(name = "idx_transaction_time", columnList = "transactionTime"),
    @Index(name = "idx_is_fraud", columnList = "isFraud"),
    @Index(name = "idx_virtual_time", columnList = "virtualTime")
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

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String merchant;

    @Column(nullable = false)
    private String merchantCategory;

    @Column(nullable = false)
    private LocalDateTime transactionTime;

    @Column(nullable = false)
    private LocalDateTime virtualTime;

    @Builder.Default
    private Boolean isFraud = false;

    @Builder.Default
    private Boolean hasGoldLabel = false;

    @Column(columnDefinition = "TEXT")
    private String anonymizedFeatures;

    @Column(precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(precision = 11, scale = 8)
    private BigDecimal longitude;

    @Column
    private String deviceFingerprint;

    @Column
    private String ipAddress;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;

    @Column
    private String externalTransactionId;

    public enum TransactionStatus {
        PENDING, PROCESSED, FAILED
    }

    public void markAsFraud() {
        this.isFraud = true;
    }

    public void setGoldLabel(Boolean isFraud) {
        this.isFraud = isFraud;
        this.hasGoldLabel = true;
    }

    public void updateStatus(TransactionStatus status) {
        this.status = status;
    }
}
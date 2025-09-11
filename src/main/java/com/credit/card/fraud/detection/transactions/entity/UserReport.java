package com.credit.card.fraud.detection.transactions.entity;

import com.credit.card.fraud.detection.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_reports", indexes = {
    @Index(name = "idx_transaction_id", columnList = "transaction_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_reported_by", columnList = "reportedBy")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserReport extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @Column(nullable = false)
    private String reportedBy;

    @Column(nullable = false)
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ReportStatus status = ReportStatus.PENDING;

    @Column
    private String reviewedBy;

    @Column(columnDefinition = "TEXT")
    private String reviewComment;

    @Builder.Default
    private Boolean isFraudConfirmed = false;

    public enum ReportStatus {
        PENDING, APPROVED, REJECTED, UNDER_REVIEW
    }

    public void approve(String reviewedBy, String comment, Boolean isFraud) {
        this.status = ReportStatus.APPROVED;
        this.reviewedBy = reviewedBy;
        this.reviewComment = comment;
        this.isFraudConfirmed = isFraud;
        
        // 트랜잭션에 골드 라벨 적용
        if (this.transaction != null) {
            this.transaction.setGoldLabel(isFraud);
        }
    }

    public void reject(String reviewedBy, String comment) {
        this.status = ReportStatus.REJECTED;
        this.reviewedBy = reviewedBy;
        this.reviewComment = comment;
    }
}
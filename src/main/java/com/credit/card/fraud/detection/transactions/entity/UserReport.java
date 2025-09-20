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
        @Index(name = "idx_reported_by", columnList = "reportedBy"),
        @Index(name = "idx_created_at", columnList = "created_at"),
        @Index(name = "idx_status_created", columnList = "status,created_at")
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

    @Column(nullable = false, length = 100)
    private String reportedBy;

    @Column(nullable = false, length = 100)
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ReportStatus status = ReportStatus.PENDING;

    @Column(length = 100)
    private String reviewedBy;

    @Column(columnDefinition = "TEXT")
    private String reviewComment;

    @Column(name = "is_fraud_confirmed")
    @Builder.Default
    private Boolean isFraudConfirmed = false;

    public enum ReportStatus {
        PENDING,      // 검토 대기
        APPROVED,     // 승인됨 (골드 라벨 적용)
        REJECTED,     // 거부됨
        UNDER_REVIEW  // 검토 중
    }

    /**
     * 신고 승인 및 골드 라벨 적용
     */
    public void approve(String reviewedBy, String comment, Boolean isFraud) {
        this.status = ReportStatus.APPROVED;
        this.reviewedBy = reviewedBy;
        this.reviewComment = comment;
        this.isFraudConfirmed = isFraud;

        // 트랜잭션에 골드 라벨 적용
        if (this.transaction != null && isFraud != null) {
            this.transaction.setGoldLabel(isFraud);
        }
    }

    /**
     * 신고 거부
     */
    public void reject(String reviewedBy, String comment) {
        this.status = ReportStatus.REJECTED;
        this.reviewedBy = reviewedBy;
        this.reviewComment = comment;
        this.isFraudConfirmed = null; // 거부된 경우 판단 초기화
    }

    /**
     * 검토 중 상태로 변경
     */
    public void setUnderReview(String reviewedBy, String comment) {
        this.status = ReportStatus.UNDER_REVIEW;
        this.reviewedBy = reviewedBy;
        this.reviewComment = comment;
    }

    /**
     * 골드 라벨 검증
     */
    public boolean hasValidGoldLabel() {
        return status == ReportStatus.APPROVED && isFraudConfirmed != null;
    }

    /**
     * 검토 완료 여부
     */
    public boolean isReviewed() {
        return status == ReportStatus.APPROVED || status == ReportStatus.REJECTED;
    }

    /**
     * 대기 중 여부
     */
    public boolean isPending() {
        return status == ReportStatus.PENDING || status == ReportStatus.UNDER_REVIEW;
    }
}
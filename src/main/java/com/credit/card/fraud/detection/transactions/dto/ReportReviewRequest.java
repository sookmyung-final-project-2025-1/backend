package com.credit.card.fraud.detection.transactions.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportReviewRequest {

    @NotBlank(message = "Reviewer information is required")
    private String reviewedBy;

    private String comment;

    private Boolean isFraud; // 골드 라벨: true=사기, false=정상, null=판단불가

    @NotBlank(message = "Action is required")
    @Pattern(regexp = "APPROVE|REJECT|UNDER_REVIEW",
             message = "Action must be one of: APPROVE, REJECT, UNDER_REVIEW")
    private String action;

    // 추가 검토 메타데이터
    private String confidence; // "HIGH", "MEDIUM", "LOW"
    private String reviewType; // "MANUAL", "AUTOMATED", "ESCALATED"
    private String escalationReason; // 에스컬레이션 사유
}
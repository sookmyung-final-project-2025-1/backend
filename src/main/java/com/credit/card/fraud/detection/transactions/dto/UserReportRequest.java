package com.credit.card.fraud.detection.transactions.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserReportRequest {

    @NotNull(message = "Transaction ID is required")
    private Long transactionId;

    @NotBlank(message = "Reporter information is required")
    private String reportedBy;

    @NotBlank(message = "Reason is required")
    private String reason;

    private String description;

    // 선택적 메타데이터
    private String reporterType; // "USER", "SYSTEM", "ADMIN"
    private String severity; // "LOW", "MEDIUM", "HIGH", "CRITICAL"
    private String category; // "FRAUD", "ERROR", "DISPUTE", "OTHER"
}
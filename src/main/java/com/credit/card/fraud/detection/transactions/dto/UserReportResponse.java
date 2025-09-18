package com.credit.card.fraud.detection.transactions.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserReportResponse {

    private Long reportId;
    private Long transactionId;
    private String reportedBy;
    private String reason;
    private String description;
    private String status;
    private String reviewedBy;
    private String reviewComment;
    private Boolean isFraudConfirmed;
    private LocalDateTime reportedAt;
    private LocalDateTime reviewedAt;

    // 거래 상세 정보
    private Map<String, Object> transactionDetails;

    // 추가 메타데이터
    private String priority;
    private String severity;
    private String category;
    private String message; // 응답 메시지
}
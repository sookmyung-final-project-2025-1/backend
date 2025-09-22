package com.credit.card.fraud.detection.transactions.dto;

import com.credit.card.fraud.detection.transactions.entity.UserReport;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "사용자 신고 DTO")
public class UserReportDto {

    @Schema(description = "신고 ID", example = "1")
    private Long id;

    @Schema(description = "신고자", example = "USER_789")
    private String reportedBy;

    @Schema(description = "신고 사유", example = "SUSPICIOUS_PATTERN")
    private String reason;

    @Schema(description = "신고 설명", example = "비정상적인 거래 패턴 발견")
    private String description;

    @Schema(description = "신고 상태", example = "PENDING")
    private UserReport.ReportStatus status;

    @Schema(description = "검토자", example = "ADMIN_123")
    private String reviewedBy;

    @Schema(description = "생성 시간")
    private LocalDateTime createdAt;

    @Schema(description = "수정 시간")
    private LocalDateTime updatedAt;

    /**
     * UserReport 엔티티를 UserReportDto로 변환
     */
    public static UserReportDto from(UserReport userReport) {
        return UserReportDto.builder()
                .id(userReport.getId())
                .reportedBy(userReport.getReportedBy())
                .reason(userReport.getReason())
                .description(userReport.getDescription())
                .status(userReport.getStatus())
                .reviewedBy(userReport.getReviewedBy())
                .createdAt(userReport.getCreatedAt())
                .updatedAt(userReport.getUpdatedAt())
                .build();
    }
}
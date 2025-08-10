package com.credit.card.fraud.detection.auth.dto.request;

import com.credit.card.fraud.detection.company.dto.Industry;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SignupRequest {
    @NotBlank(message = "기업명은 필수입니다.")
    @Size(min = 2, max = 100, message = "기업명은 2자 이상 100자 이하여야 합니다.")
    private String name;

    @NotBlank(message = "사업자등록번호는 필수입니다.")
    @Pattern(regexp = "^\\d{3}-\\d{2}-\\d{5}$", message = "사업자등록번호는 000-00-00000 형식이어야 합니다.")
    private String businessNumber;

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    private String email;

    @NotBlank(message = "비밀번호는 필수입니다.")
    @Size(min = 8, max = 20, message = "비밀번호는 8자 이상 20자 이하여야 합니다.")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
            message = "비밀번호는 대소문자, 숫자, 특수문자를 포함해야 합니다.")
    private String password;

    @NotBlank(message = "담당자명은 필수입니다.")
    @Size(min = 2, max = 20, message = "담당자명은 2자 이상 20자 이하여야 합니다.")
    private String managerName;

    @NotNull(message = "업종은 필수입니다.")
    private Industry industry;
}
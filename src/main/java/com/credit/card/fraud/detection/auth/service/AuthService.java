package com.credit.card.fraud.detection.auth.service;

import com.credit.card.fraud.detection.auth.dto.request.EmailVerificationCodeRequest;
import com.credit.card.fraud.detection.auth.dto.request.EmailVerificationRequest;
import com.credit.card.fraud.detection.auth.dto.request.LoginRequest;
import com.credit.card.fraud.detection.auth.dto.request.SignupRequest;
import com.credit.card.fraud.detection.auth.dto.response.AuthResponse;
import com.credit.card.fraud.detection.auth.dto.response.EmailVerificationResponse;
import com.credit.card.fraud.detection.company.entity.Company;
import com.credit.card.fraud.detection.company.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;

    /**
     * 이메일 인증 코드 발송
     */
    public EmailVerificationResponse sendVerificationCode(EmailVerificationRequest request) {
        String email = request.getEmail();

        log.info("=== 이메일 발송 시작: {} ===", email);

        if (companyRepository.existsByEmail(email)) {
            return EmailVerificationResponse.failure("이미 가입된 이메일입니다.");
        }

        try {
            log.info("EmailService.sendVerificationCode 호출 전");
            emailService.sendVerificationCode(email);
            log.info("EmailService.sendVerificationCode 호출 후");

            int expirationSeconds = emailService.getExpirationSeconds(email);
            return EmailVerificationResponse.success(
                    "인증 코드가 발송되었습니다.",
                    expirationSeconds
            );
        } catch (Exception e) {
            log.error("=== 이메일 인증 코드 발송 실패 ===");
            log.error("Email: {}", email);
            log.error("Error message: {}", e.getMessage());
            log.error("Error class: {}", e.getClass().getName());
            log.error("Full stack trace: ", e);
            return EmailVerificationResponse.failure("이메일 발송에 실패했습니다.");
        }
    }

    /**
     * 이메일 인증 코드 검증
     */
    public EmailVerificationResponse verifyEmailCode(EmailVerificationCodeRequest request) {
        boolean isValid = emailService.verifyCode(request.getEmail(), request.getCode());

        if (isValid) {
            return EmailVerificationResponse.success("이메일 인증이 완료되었습니다. 이제 회원가입을 진행하세요.", 0);
        } else {
            return EmailVerificationResponse.failure("인증 코드가 올바르지 않거나 만료되었습니다.");
        }
    }

    /**
     * 회원가입
     */
    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String email = request.getEmail();

        // 이미 가입된 이메일인지 확인
        if (companyRepository.existsByEmail(email)) {
            throw new RuntimeException("이미 가입된 이메일입니다.");
        }

        if (!emailService.isEmailVerified(email)) {
            throw new RuntimeException("이메일 인증이 완료되지 않았습니다. 먼저 이메일 인증을 완료해주세요.");
        }

        try {
            // 회사 정보 저장
            Company company = Company.builder()
                    .name(request.getName())
                    .businessNumber(request.getBusinessNumber())
                    .email(email)
                    .password(passwordEncoder.encode(request.getPassword()))
                    .managerName(request.getManagerName())
                    .industry(request.getIndustry())
                    .build();

            companyRepository.save(company);

            emailService.clearEmailVerification(email);

            String token = jwtService.generateToken(email);

            log.info("회원가입 완료: {}", email);
            return new AuthResponse(token);

        } catch (Exception e) {
            log.error("회원가입 처리 중 오류 발생: {}", email, e);
            throw new RuntimeException("회원가입 처리 중 오류가 발생했습니다.");
        }
    }

    /**
     * 로그인
     */
    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail();

        Company company = companyRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 이메일입니다."));

        if (!passwordEncoder.matches(request.getPassword(), company.getPassword())) {
            throw new RuntimeException("비밀번호가 일치하지 않습니다.");
        }

        String token = jwtService.generateToken(email);

        log.info("로그인 성공: {}", email);
        return new AuthResponse(token);
    }

    /**
     * 사업자등록번호 중복 확인
     */
    public boolean isBusinessNumberExists(String businessNumber) {
        return companyRepository.existsByBusinessNumber(businessNumber);
    }

    /**
     * 이메일 중복 확인
     */
    public boolean isEmailExists(String email) {
        return companyRepository.existsByEmail(email);
    }
}
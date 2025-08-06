package com.credit.card.fraud.detection.auth.controller;

import com.credit.card.fraud.detection.auth.dto.request.EmailVerificationCodeRequest;
import com.credit.card.fraud.detection.auth.dto.request.EmailVerificationRequest;
import com.credit.card.fraud.detection.auth.dto.request.LoginRequest;
import com.credit.card.fraud.detection.auth.dto.request.SignupRequest;
import com.credit.card.fraud.detection.auth.dto.response.AuthResponse;
import com.credit.card.fraud.detection.auth.dto.response.EmailVerificationResponse;
import com.credit.card.fraud.detection.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "인증 API", description = "회원가입, 로그인 및 이메일 인증 관련 API")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "이메일 인증 코드 발송", description = "회원가입을 위한 이메일 인증 코드를 발송합니다.")
    @PostMapping("/send-verification-code")
    public ResponseEntity<EmailVerificationResponse> sendVerificationCode(@Valid @RequestBody EmailVerificationRequest request) {
        EmailVerificationResponse response = authService.sendVerificationCode(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "이메일 인증 코드 확인", description = "발송된 인증 코드를 확인합니다.")
    @PostMapping("/verify-email")
    public ResponseEntity<EmailVerificationResponse> verifyEmailCode(@Valid @RequestBody EmailVerificationCodeRequest request) {
        EmailVerificationResponse response = authService.verifyEmailCode(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "이메일 중복 확인", description = "이메일 중복 여부를 확인합니다.")
    @GetMapping("/check-email")
    public ResponseEntity<Map<String, Object>> checkEmail(@RequestParam String email) {
        boolean exists = authService.isEmailExists(email);
        return ResponseEntity.ok(Map.of(
                "exists", exists,
                "message", exists ? "이미 사용 중인 이메일입니다." : "사용 가능한 이메일입니다."
        ));
    }

    @Operation(summary = "사업자등록번호 중복 확인", description = "사업자등록번호 중복 여부를 확인합니다.")
    @GetMapping("/check-business-number")
    public ResponseEntity<Map<String, Object>> checkBusinessNumber(@RequestParam String businessNumber) {
        boolean exists = authService.isBusinessNumberExists(businessNumber);
        return ResponseEntity.ok(Map.of(
                "exists", exists,
                "message", exists ? "이미 등록된 사업자등록번호입니다." : "사용 가능한 사업자등록번호입니다."
        ));
    }

    @Operation(summary = "회원가입", description = "기업 회원가입을 처리합니다.")
    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        try {
            AuthResponse response = authService.signup(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new AuthResponse(null));
        }
    }

    @Operation(summary = "로그인", description = "JWT 토큰을 발급받습니다.")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            AuthResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new AuthResponse(null));
        }
    }
}
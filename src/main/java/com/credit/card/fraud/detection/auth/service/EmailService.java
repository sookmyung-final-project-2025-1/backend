package com.credit.card.fraud.detection.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@Profile("!test")  // test 프로파일이 아닐 때만 활성화
public class EmailService {

    private final JavaMailSender mailSender;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.email.verification.expiration:300}")
    private int verificationExpiration;

    @Value("${app.email.verification.template.subject}")
    private String emailSubject;

    @Value("${app.email.verification.template.content}")
    private String emailTemplate;

    private static final String EMAIL_VERIFICATION_PREFIX = "email_verification:";
    private static final String EMAIL_VERIFIED_PREFIX = "email_verified:";
    private final SecureRandom random = new SecureRandom();

    /**
     * 이메일 인증 코드 발송
     */
    public void sendVerificationCode(String email) {
        try {
            // 6자리 랜덤 코드 생성
            String code = generateVerificationCode();

            // Redis에 인증 코드 저장 (5분 만료)
            String key = EMAIL_VERIFICATION_PREFIX + email;
            redisTemplate.opsForValue().set(key, code, verificationExpiration, TimeUnit.SECONDS);

            // 이메일 발송
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email);
            message.setSubject(emailSubject);
            message.setText(String.format(emailTemplate, code));

            mailSender.send(message);

            log.info("이메일 인증 코드 발송 완료: {}", email);

        } catch (Exception e) {
            log.error("이메일 발송 실패: {}", email, e);
            throw new RuntimeException("이메일 발송에 실패했습니다.");
        }
    }

    /**
     * 이메일 인증 코드 검증
     */
    public boolean verifyCode(String email, String code) {
        try {
            String key = EMAIL_VERIFICATION_PREFIX + email;
            String storedCode = (String) redisTemplate.opsForValue().get(key);

            if (storedCode == null) {
                log.warn("만료되거나 존재하지 않는 인증 코드: {}", email);
                return false;
            }

            if (!storedCode.equals(code)) {
                log.warn("잘못된 인증 코드: {}", email);
                return false;
            }

            redisTemplate.delete(key);
            String verifiedKey = EMAIL_VERIFIED_PREFIX + email;
            redisTemplate.opsForValue().set(verifiedKey, "true", 60, TimeUnit.MINUTES);

            log.info("이메일 인증 성공: {} (1시간 동안 유효)", email);
            return true;

        } catch (Exception e) {
            log.error("이메일 인증 검증 중 오류 발생: {}", email, e);
            return false;
        }
    }

    /**
     * 이메일 인증 완료 상태 확인
     */
    public boolean isEmailVerified(String email) {
        try {
            String verifiedKey = EMAIL_VERIFIED_PREFIX + email;
            String verified = (String) redisTemplate.opsForValue().get(verifiedKey);
            return "true".equals(verified);
        } catch (Exception e) {
            log.error("이메일 인증 상태 확인 중 오류 발생: {}", email, e);
            return false;
        }
    }

    /**
     * 이메일 인증 완료 상태 삭제 (회원가입 완료 후)
     */
    public void clearEmailVerification(String email) {
        try {
            String verifiedKey = EMAIL_VERIFIED_PREFIX + email;
            redisTemplate.delete(verifiedKey);
            log.info("이메일 인증 상태 정리: {}", email);
        } catch (Exception e) {
            log.error("이메일 인증 상태 정리 중 오류 발생: {}", email, e);
        }
    }

    /**
     * 인증 상태 남은 유효시간 조회 (분 단위)
     */
    public int getVerificationRemainingMinutes(String email) {
        String verifiedKey = EMAIL_VERIFIED_PREFIX + email;
        Long expire = redisTemplate.getExpire(verifiedKey, TimeUnit.MINUTES);
        return expire != null ? expire.intValue() : 0;
    }

    /**
     * 6자리 인증 코드 생성
     */
    private String generateVerificationCode() {
        return String.format("%06d", random.nextInt(1000000));
    }

    /**
     * 남은 만료 시간 조회 (초 단위) - 인증 코드용
     */
    public int getExpirationSeconds(String email) {
        String key = EMAIL_VERIFICATION_PREFIX + email;
        Long expire = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return expire != null ? expire.intValue() : 0;
    }
}
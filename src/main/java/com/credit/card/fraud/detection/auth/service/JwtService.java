package com.credit.card.fraud.detection.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtService {

    private final SecretKey secretKey;
    private final long expireTime;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.access-token-validity-in-seconds:3600}") long expireTime) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireTime = expireTime * 1000;
    }

    /**
     * JWT 토큰 생성
     */
    public String generateToken(String email) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expireTime);

        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(now)
                .setExpiration(expiration)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * JWT 토큰에서 이메일 추출
     */
    public String extractEmail(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            return claims.getSubject();
        } catch (Exception e) {
            log.error("JWT 토큰에서 이메일 추출 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * JWT 토큰 유효성 검증
     */
    public boolean isTokenValid(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            // 만료 시간 확인
            Date expiration = claims.getExpiration();
            return expiration.after(new Date());

        } catch (Exception e) {
            log.error("JWT 토큰 검증 실패: {}", e.getMessage());
            return false;
        }
    }

    /**
     * JWT 토큰 만료 시간 조회
     */
    public Date getExpirationDate(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            return claims.getExpiration();
        } catch (Exception e) {
            log.error("JWT 토큰 만료 시간 조회 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * JWT 토큰 발급 시간 조회
     */
    public Date getIssuedDate(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            return claims.getIssuedAt();
        } catch (Exception e) {
            log.error("JWT 토큰 발급 시간 조회 실패: {}", e.getMessage());
            return null;
        }
    }
}
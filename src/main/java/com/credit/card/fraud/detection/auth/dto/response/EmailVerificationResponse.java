package com.credit.card.fraud.detection.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class EmailVerificationResponse {
    private boolean success;
    private String message;
    private int expirationSeconds;

    public static EmailVerificationResponse success(String message, int expirationSeconds) {
        return new EmailVerificationResponse(true, message, expirationSeconds);
    }

    public static EmailVerificationResponse failure(String message) {
        return new EmailVerificationResponse(false, message, 0);
    }
}
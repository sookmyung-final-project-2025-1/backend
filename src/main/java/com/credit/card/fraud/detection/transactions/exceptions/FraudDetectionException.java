package com.credit.card.fraud.detection.transactions.exceptions;

public class FraudDetectionException extends RuntimeException {
    public FraudDetectionException(String message) {
        super(message);
    }

    public FraudDetectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
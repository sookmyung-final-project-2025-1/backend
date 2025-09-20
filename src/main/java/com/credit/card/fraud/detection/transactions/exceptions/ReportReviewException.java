package com.credit.card.fraud.detection.transactions.exceptions;

public class ReportReviewException extends RuntimeException {
    public ReportReviewException(String message, Throwable cause) {
        super(message, cause);
    }
}
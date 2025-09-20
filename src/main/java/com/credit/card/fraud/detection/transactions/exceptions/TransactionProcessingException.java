package com.credit.card.fraud.detection.transactions.exceptions;

public class TransactionProcessingException extends RuntimeException {
    public TransactionProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
package com.credit.card.fraud.detection.transactions.exceptions;

public class TransactionQueryException extends RuntimeException {
    public TransactionQueryException(String message, Throwable cause) {
        super(message, cause);
    }
}
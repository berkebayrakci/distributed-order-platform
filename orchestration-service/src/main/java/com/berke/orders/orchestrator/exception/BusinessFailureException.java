package com.berke.orders.orchestrator.exception;

public class BusinessFailureException extends RuntimeException {
    public BusinessFailureException(String message) {
        super(message);
    }
}

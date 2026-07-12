package com.berke.orders.orchestrator.exception;

public class TransientInfrastructureException extends RuntimeException {
    public TransientInfrastructureException(String message, Throwable cause) {
        super(message, cause);
    }
}

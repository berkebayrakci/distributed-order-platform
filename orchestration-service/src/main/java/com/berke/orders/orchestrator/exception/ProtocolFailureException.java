package com.berke.orders.orchestrator.exception;

public class ProtocolFailureException extends RuntimeException {
    public ProtocolFailureException(String message) {
        super(message);
    }

    public ProtocolFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}

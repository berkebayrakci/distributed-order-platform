package com.berke.orders.orchestrator.model;

public enum CallbackOutboxStatus {
    PENDING,
    PROCESSING,
    RETRY_PENDING,
    DELIVERED,
    DEAD
}

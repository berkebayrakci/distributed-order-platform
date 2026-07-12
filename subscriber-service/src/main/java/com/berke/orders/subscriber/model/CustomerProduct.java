package com.berke.orders.subscriber.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.*;

@Entity
@Table(name = "customer_product")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerProduct {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    String customerId;
    String targetProductCode;
    String targetItemRef;
    String productType;
    Integer productVersion;
    String validityType;
    Integer validityAmount;
    String validityUnit;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    ProductLifecycleStatus status;
    Instant activatedAt;
    Instant expiresAt;
    Instant expiredAt;
    Instant terminatedAt;
    String terminationReason;
    Long activationOrderId;
    Long terminationOrderId;
    @Version
    @Column(name = "version", nullable = false)
    Long version;
    LocalDateTime createdAt;

    @PrePersist
    void pre() {
        createdAt = LocalDateTime.now();
        if (status == null) status = ProductLifecycleStatus.PENDING;
    }

    public void activate(Long orderId, Instant activationTime, Instant expiryTime) {
        requireStatus(ProductLifecycleStatus.PENDING, "activate");
        Long validatedOrderId = requireOrderId(orderId, "Activation");
        Instant validatedActivationTime = requireTime(activationTime, "Activation time is required");
        if (expiryTime != null && expiryTime.isBefore(validatedActivationTime)) {
            throw new IllegalArgumentException("Expiry time cannot be before activation time");
        }
        if ("FIXED_DURATION".equals(validityType) && expiryTime == null) {
            throw new IllegalArgumentException("Fixed-duration product requires an expiry time");
        }
        if ("NON_EXPIRING".equals(validityType) && expiryTime != null) {
            throw new IllegalArgumentException("Non-expiring product cannot have an expiry time");
        }
        activationOrderId = validatedOrderId;
        activatedAt = validatedActivationTime;
        expiresAt = expiryTime;
        status = ProductLifecycleStatus.ACTIVE;
    }

    public void suspend() {
        requireStatus(ProductLifecycleStatus.ACTIVE, "suspend");
        status = ProductLifecycleStatus.SUSPENDED;
    }

    public void resume() {
        requireStatus(ProductLifecycleStatus.SUSPENDED, "resume");
        status = ProductLifecycleStatus.ACTIVE;
    }

    public void expire(Instant expiryTime) {
        requireOneOf("expire", ProductLifecycleStatus.ACTIVE, ProductLifecycleStatus.SUSPENDED);
        Instant validatedExpiryTime = requireTime(expiryTime, "Expired time is required");
        if (activatedAt != null && validatedExpiryTime.isBefore(activatedAt)) {
            throw new IllegalArgumentException("Expired time cannot be before activation time");
        }
        expiredAt = validatedExpiryTime;
        status = ProductLifecycleStatus.EXPIRED;
    }

    public void terminate(Long orderId, Instant terminationTime, String reason) {
        requireOneOf("terminate", ProductLifecycleStatus.ACTIVE, ProductLifecycleStatus.SUSPENDED);
        Long validatedOrderId = requireOrderId(orderId, "Termination");
        Instant validatedTerminationTime = requireTime(terminationTime, "Termination time is required");
        if (activatedAt != null && validatedTerminationTime.isBefore(activatedAt)) {
            throw new IllegalArgumentException("Termination time cannot be before activation time");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Termination reason is required");
        }
        terminationOrderId = validatedOrderId;
        terminatedAt = validatedTerminationTime;
        terminationReason = reason.trim();
        status = ProductLifecycleStatus.TERMINATED;
    }

    public void cancel() {
        requireStatus(ProductLifecycleStatus.PENDING, "cancel");
        status = ProductLifecycleStatus.CANCELLED;
    }

    public void fail() {
        requireStatus(ProductLifecycleStatus.PENDING, "fail");
        status = ProductLifecycleStatus.FAILED;
    }

    private void requireStatus(ProductLifecycleStatus expected, String action) {
        if (status != expected) {
            throw new IllegalStateException("Cannot " + action + " product in status " + status);
        }
    }

    private void requireOneOf(String action, ProductLifecycleStatus first, ProductLifecycleStatus second) {
        if (status != first && status != second) {
            throw new IllegalStateException("Cannot " + action + " product in status " + status);
        }
    }

    private Long requireOrderId(Long orderId, String operation) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException(operation + " order ID must be positive");
        }
        return orderId;
    }

    private Instant requireTime(Instant time, String message) {
        if (time == null) {
            throw new IllegalArgumentException(message);
        }
        return time;
    }
}

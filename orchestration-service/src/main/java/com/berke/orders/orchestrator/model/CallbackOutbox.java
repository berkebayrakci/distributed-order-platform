package com.berke.orders.orchestrator.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "callback_outbox")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallbackOutbox {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private UUID eventId;

    @Column(nullable = false)
    private String correlationId;

    @Column(nullable = false, length = 64)
    private String operationType;

    @Column(nullable = false)
    private Long operationId;

    @Column(nullable = false, length = 2048)
    private String callbackUrl;

    @Column(nullable = false, length = 16)
    private String httpMethod;

    @Column(nullable = false, columnDefinition = "text")
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CallbackOutboxStatus status;

    @Column(nullable = false)
    private int attemptCount;

    @Column(nullable = false)
    private int maxAttempts;

    private Instant nextAttemptAt;
    private Instant lastAttemptAt;

    @Column(columnDefinition = "text")
    private String lastError;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant deliveredAt;

    @Version
    private long version;

    @PrePersist
    void prePersist() {
        if (eventId == null) eventId = UUID.randomUUID();
        if (httpMethod == null) httpMethod = "POST";
        if (status == null) status = CallbackOutboxStatus.PENDING;
        if (createdAt == null) createdAt = Instant.now();
        if (nextAttemptAt == null) nextAttemptAt = createdAt;
    }
}

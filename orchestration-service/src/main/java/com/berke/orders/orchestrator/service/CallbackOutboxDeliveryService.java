package com.berke.orders.orchestrator.service;

import com.berke.orders.orchestrator.config.CallbackOutboxProperties;
import com.berke.orders.orchestrator.model.CallbackOutbox;
import com.berke.orders.orchestrator.model.CallbackOutboxStatus;
import com.berke.orders.orchestrator.repo.CallbackOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CallbackOutboxDeliveryService {
    private static final int MAX_ERROR_LENGTH = 4000;

    private final CallbackOutboxRepository outboxRepository;
    private final CallbackOutboxProperties properties;

    @Transactional
    public List<Claim> claimDue() {
        Instant claimedAt = Instant.now();
        outboxRepository.markExpiredFinalAttemptsDead();
        List<CallbackOutbox> rows = outboxRepository.findDueForUpdate(properties.getBatchSize());
        rows.forEach(row -> {
            row.setStatus(CallbackOutboxStatus.PROCESSING);
            row.setAttemptCount(row.getAttemptCount() + 1);
            row.setLastAttemptAt(claimedAt);
            row.setNextAttemptAt(claimedAt.plus(properties.getProcessingTimeout()));
        });
        outboxRepository.flush();
        return rows.stream().map(Claim::from).toList();
    }

    @Transactional
    public boolean markDelivered(Claim claim) {
        var row = currentClaim(claim);
        if (row == null) return false;

        row.setStatus(CallbackOutboxStatus.DELIVERED);
        row.setDeliveredAt(Instant.now());
        row.setNextAttemptAt(null);
        row.setLastError(null);
        return true;
    }

    @Transactional
    public CallbackOutboxStatus recordFailure(Claim claim, Exception failure) {
        var row = currentClaim(claim);
        if (row == null) return null;

        row.setLastError(limitError(failure));
        if (row.getAttemptCount() >= row.getMaxAttempts()) {
            row.setStatus(CallbackOutboxStatus.DEAD);
            row.setNextAttemptAt(null);
        } else {
            row.setStatus(CallbackOutboxStatus.RETRY_PENDING);
            row.setNextAttemptAt(Instant.now().plus(retryDelay(row.getAttemptCount())));
        }
        return row.getStatus();
    }

    private CallbackOutbox currentClaim(Claim claim) {
        var row = outboxRepository.findById(claim.id()).orElse(null);
        if (row == null
                || row.getStatus() != CallbackOutboxStatus.PROCESSING
                || row.getVersion() != claim.version()) {
            return null;
        }
        return row;
    }

    private Duration retryDelay(int attemptCount) {
        double scaledMillis = properties.getInitialBackoff().toMillis()
                * Math.pow(properties.getBackoffMultiplier(), Math.max(0, attemptCount - 1));
        long delayMillis = (long) Math.min(scaledMillis, properties.getMaxBackoff().toMillis());
        return Duration.ofMillis(Math.max(0, delayMillis));
    }

    private String limitError(Exception failure) {
        String message = failure.getClass().getSimpleName() + ": "
                + (failure.getMessage() == null ? "No error message" : failure.getMessage());
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }

    public record Claim(
            Long id,
            UUID eventId,
            String operationType,
            Long operationId,
            String callbackUrl,
            String payloadJson,
            int attemptCount,
            long version
    ) {
        static Claim from(CallbackOutbox row) {
            return new Claim(
                    row.getId(),
                    row.getEventId(),
                    row.getOperationType(),
                    row.getOperationId(),
                    row.getCallbackUrl(),
                    row.getPayloadJson(),
                    row.getAttemptCount(),
                    row.getVersion()
            );
        }
    }
}

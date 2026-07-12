package com.berke.orders.orchestrator.service;

import com.berke.orders.orchestrator.config.CallbackOutboxProperties;
import com.berke.orders.orchestrator.model.CallbackOutbox;
import com.berke.orders.orchestrator.model.CallbackOutboxStatus;
import com.berke.orders.orchestrator.repo.CallbackOutboxRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallbackOutboxDeliveryServiceTest {

    @Test
    void claimMarksRowsProcessingAndCreatesARecoveryLease() {
        var repository = mock(CallbackOutboxRepository.class);
        var properties = properties();
        var row = pendingRow(1, 3);
        when(repository.findDueForUpdate(20)).thenReturn(List.of(row));
        var service = new CallbackOutboxDeliveryService(repository, properties);
        Instant before = Instant.now();

        var claims = service.claimDue();

        assertEquals(1, claims.size());
        assertEquals(CallbackOutboxStatus.PROCESSING, row.getStatus());
        assertEquals(1, row.getAttemptCount());
        assertFalse(row.getNextAttemptAt().isBefore(before.plus(properties.getProcessingTimeout())));
        verify(repository).markExpiredFinalAttemptsDead();
        verify(repository).flush();
    }

    @Test
    void failedAttemptIsRescheduledWithBackoff() {
        var repository = mock(CallbackOutboxRepository.class);
        var properties = properties();
        var row = processingRow(1, 3);
        var claim = claim(row);
        when(repository.findById(row.getId())).thenReturn(Optional.of(row));
        var service = new CallbackOutboxDeliveryService(repository, properties);
        Instant before = Instant.now();

        var status = service.recordFailure(claim, new IllegalStateException("CRM unavailable"));

        assertEquals(CallbackOutboxStatus.RETRY_PENDING, status);
        assertTrue(row.getNextAttemptAt().compareTo(before.plusSeconds(1)) >= 0);
        assertTrue(row.getLastError().contains("CRM unavailable"));
    }

    @Test
    void exhaustedAttemptBecomesDead() {
        var repository = mock(CallbackOutboxRepository.class);
        var properties = properties();
        var row = processingRow(3, 3);
        var claim = claim(row);
        when(repository.findById(row.getId())).thenReturn(Optional.of(row));
        var service = new CallbackOutboxDeliveryService(repository, properties);

        var status = service.recordFailure(claim, new IllegalStateException("still unavailable"));

        assertEquals(CallbackOutboxStatus.DEAD, status);
        assertNull(row.getNextAttemptAt());
    }

    @Test
    void successfulAttemptBecomesDelivered() {
        var repository = mock(CallbackOutboxRepository.class);
        var row = processingRow(1, 3);
        var claim = claim(row);
        when(repository.findById(row.getId())).thenReturn(Optional.of(row));
        var service = new CallbackOutboxDeliveryService(repository, properties());

        assertTrue(service.markDelivered(claim));

        assertEquals(CallbackOutboxStatus.DELIVERED, row.getStatus());
        assertNotNull(row.getDeliveredAt());
        assertNull(row.getNextAttemptAt());
        assertNull(row.getLastError());
    }

    private CallbackOutboxProperties properties() {
        var properties = new CallbackOutboxProperties();
        properties.setBatchSize(20);
        properties.setInitialBackoff(Duration.ofSeconds(1));
        properties.setBackoffMultiplier(2.0);
        properties.setMaxBackoff(Duration.ofSeconds(10));
        properties.setProcessingTimeout(Duration.ofMinutes(2));
        return properties;
    }

    private CallbackOutbox pendingRow(int attempts, int maxAttempts) {
        return CallbackOutbox.builder()
                .id(7L)
                .eventId(UUID.randomUUID())
                .operationType("PRODUCT_ORDER")
                .operationId(42L)
                .callbackUrl("http://crm-service/api/orders/callback")
                .payloadJson("{\"orderId\":42,\"status\":\"COMPLETED\",\"errorMessage\":null}")
                .status(CallbackOutboxStatus.PENDING)
                .attemptCount(attempts - 1)
                .maxAttempts(maxAttempts)
                .version(4L)
                .build();
    }

    private CallbackOutbox processingRow(int attempts, int maxAttempts) {
        var row = pendingRow(attempts, maxAttempts);
        row.setStatus(CallbackOutboxStatus.PROCESSING);
        row.setAttemptCount(attempts);
        return row;
    }

    private CallbackOutboxDeliveryService.Claim claim(CallbackOutbox row) {
        return new CallbackOutboxDeliveryService.Claim(
                row.getId(), row.getEventId(), row.getOperationType(), row.getOperationId(),
                row.getCallbackUrl(), row.getPayloadJson(), row.getAttemptCount(), row.getVersion());
    }
}

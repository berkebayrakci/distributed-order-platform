package com.berke.orders.orchestrator.service;

import com.berke.orders.orchestrator.model.CallbackOutboxStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallbackOutboxWorkerTest {

    @Test
    void unavailableCallbackIsRetriedAndEventuallyDelivered() {
        var deliveryService = mock(CallbackOutboxDeliveryService.class);
        var callbackClient = mock(CallbackClient.class);
        var worker = new CallbackOutboxWorker(deliveryService, callbackClient, new ObjectMapper());
        UUID eventId = UUID.randomUUID();
        var firstAttempt = claim(eventId, 1);
        var secondAttempt = claim(eventId, 2);
        when(deliveryService.claimDue())
                .thenReturn(List.of(firstAttempt))
                .thenReturn(List.of(secondAttempt));
        doThrow(new ResourceAccessException("CRM unavailable"))
                .doNothing()
                .when(callbackClient).postOnce(
                        firstAttempt.callbackUrl(),
                        new com.berke.orders.orchestrator.dto.OrchestratorDtos.ProductOrderCallback(42L, "COMPLETED", null),
                        eventId);
        when(deliveryService.recordFailure(
                org.mockito.ArgumentMatchers.eq(firstAttempt),
                org.mockito.ArgumentMatchers.any(ResourceAccessException.class)))
                .thenReturn(CallbackOutboxStatus.RETRY_PENDING);
        when(deliveryService.markDelivered(secondAttempt)).thenReturn(true);

        worker.deliverDueCallbacks();
        worker.deliverDueCallbacks();

        verify(deliveryService).recordFailure(
                org.mockito.ArgumentMatchers.eq(firstAttempt),
                org.mockito.ArgumentMatchers.any(ResourceAccessException.class));
        verify(deliveryService).markDelivered(secondAttempt);
        verify(callbackClient, times(2)).postOnce(
                org.mockito.ArgumentMatchers.eq(firstAttempt.callbackUrl()),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(eventId));
    }

    @Test
    void exhaustedCallbackIsRecordedDead() {
        var deliveryService = mock(CallbackOutboxDeliveryService.class);
        var callbackClient = mock(CallbackClient.class);
        var worker = new CallbackOutboxWorker(deliveryService, callbackClient, new ObjectMapper());
        var claim = claim(UUID.randomUUID(), 6);
        var failure = new ResourceAccessException("CRM unavailable");
        when(deliveryService.claimDue()).thenReturn(List.of(claim));
        doThrow(failure).when(callbackClient).postOnce(
                org.mockito.ArgumentMatchers.eq(claim.callbackUrl()),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(claim.eventId()));
        when(deliveryService.recordFailure(claim, failure)).thenReturn(CallbackOutboxStatus.DEAD);

        worker.deliverDueCallbacks();

        verify(deliveryService).recordFailure(claim, failure);
    }

    private CallbackOutboxDeliveryService.Claim claim(UUID eventId, int attempt) {
        return new CallbackOutboxDeliveryService.Claim(
                7L,
                eventId,
                "PRODUCT_ORDER",
                42L,
                "http://crm-service/api/orders/callback",
                "{\"orderId\":42,\"status\":\"COMPLETED\",\"errorMessage\":null}",
                attempt,
                attempt
        );
    }
}

package com.berke.orders.orchestrator.service;

import com.berke.orders.orchestrator.config.CallbackOutboxProperties;
import com.berke.orders.orchestrator.model.CallbackOutbox;
import com.berke.orders.orchestrator.model.CallbackOutboxStatus;
import com.berke.orders.orchestrator.model.ProductOrder;
import com.berke.orders.orchestrator.repo.CallbackOutboxRepository;
import com.berke.orders.orchestrator.repo.ProductOrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductOrderFinalizationServiceTest {

    @Test
    void terminalOrderAndCallbackAreSavedByTheSameTransactionalMethod() throws Exception {
        var orderRepository = mock(ProductOrderRepository.class);
        var outboxRepository = mock(CallbackOutboxRepository.class);
        var properties = new CallbackOutboxProperties();
        properties.setMaxAttempts(6);
        var objectMapper = new ObjectMapper();
        var service = new ProductOrderFinalizationService(
                orderRepository, outboxRepository, properties, objectMapper);
        var order = ProductOrder.builder()
                .orderId(42L)
                .customerId("customer-1")
                .crmCallbackUrl("http://crm-service/api/orders/callback")
                .status("IN_PROGRESS")
                .build();
        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));

        assertTrue(service.complete(42L, 9001L));

        verify(orderRepository).save(order);
        var outboxCaptor = ArgumentCaptor.forClass(CallbackOutbox.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        CallbackOutbox outbox = outboxCaptor.getValue();

        assertEquals("COMPLETED", order.getStatus());
        assertEquals(9001L, order.getUniversalProductKey());
        assertEquals(42L, outbox.getOperationId());
        assertEquals("PRODUCT_ORDER", outbox.getOperationType());
        assertEquals(CallbackOutboxStatus.PENDING, outbox.getStatus());
        assertEquals(0, outbox.getAttemptCount());
        assertEquals(6, outbox.getMaxAttempts());
        assertEquals("COMPLETED", objectMapper.readTree(outbox.getPayloadJson()).get("status").asText());
        assertNotNull(ProductOrderFinalizationService.class
                .getMethod("complete", Long.class, Long.class)
                .getAnnotation(Transactional.class));
    }

    @Test
    void failedOrderAlsoQueuesTheExistingCallbackContract() throws Exception {
        var orderRepository = mock(ProductOrderRepository.class);
        var outboxRepository = mock(CallbackOutboxRepository.class);
        var properties = new CallbackOutboxProperties();
        var objectMapper = new ObjectMapper();
        var service = new ProductOrderFinalizationService(
                orderRepository, outboxRepository, properties, objectMapper);
        var order = ProductOrder.builder()
                .orderId(43L)
                .crmCallbackUrl("http://crm-service/api/orders/callback")
                .status("IN_PROGRESS")
                .build();
        when(orderRepository.findById(43L)).thenReturn(Optional.of(order));

        assertTrue(service.fail(43L, "downstream failure"));

        var outboxCaptor = ArgumentCaptor.forClass(CallbackOutbox.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        var payload = objectMapper.readTree(outboxCaptor.getValue().getPayloadJson());
        assertEquals("FAILED", order.getStatus());
        assertEquals("downstream failure", payload.get("errorMessage").asText());
    }
}

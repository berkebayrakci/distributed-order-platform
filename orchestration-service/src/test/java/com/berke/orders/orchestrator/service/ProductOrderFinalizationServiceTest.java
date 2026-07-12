package com.berke.orders.orchestrator.service;

import com.berke.orders.orchestrator.config.CallbackOutboxProperties;
import com.berke.orders.orchestrator.model.CallbackOutbox;
import com.berke.orders.orchestrator.model.CallbackOutboxStatus;
import com.berke.orders.orchestrator.model.OrderStatus;
import com.berke.orders.orchestrator.model.ProductOrder;
import com.berke.orders.orchestrator.repo.CallbackOutboxRepository;
import com.berke.orders.orchestrator.repo.ProductOrderRepository;
import com.berke.orders.orchestrator.repo.ProcessedEventRepository;
import com.berke.orders.orchestrator.dto.OrchestratorDtos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.util.List;

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
                .correlationId(UUID.randomUUID())
                .crmCallbackUrl("http://crm-service/api/orders/callback")
                .status(OrderStatus.FINALIZING)
                .build();
        when(orderRepository.completeFinalization(
                42L, 9001L, OrderStatus.FINALIZING, OrderStatus.COMPLETED)).thenReturn(1);
        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));

        assertTrue(service.complete(42L, 9001L));

        verify(orderRepository).completeFinalization(
                42L, 9001L, OrderStatus.FINALIZING, OrderStatus.COMPLETED);
        var outboxCaptor = ArgumentCaptor.forClass(CallbackOutbox.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        CallbackOutbox outbox = outboxCaptor.getValue();

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
                .correlationId(UUID.randomUUID())
                .crmCallbackUrl("http://crm-service/api/orders/callback")
                .status(OrderStatus.FINALIZING)
                .build();
        when(orderRepository.failActiveOrder(
                43L,
                "downstream failure",
                OrderStatus.IN_PROGRESS,
                OrderStatus.FINALIZING,
                OrderStatus.FAILED
        )).thenReturn(1);
        when(orderRepository.findById(43L)).thenReturn(Optional.of(order));

        assertTrue(service.fail(43L, "downstream failure"));

        var outboxCaptor = ArgumentCaptor.forClass(CallbackOutbox.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        var payload = objectMapper.readTree(outboxCaptor.getValue().getPayloadJson());
        verify(orderRepository).failActiveOrder(
                43L,
                "downstream failure",
                OrderStatus.IN_PROGRESS,
                OrderStatus.FINALIZING,
                OrderStatus.FAILED
        );
        assertEquals("downstream failure", payload.get("errorMessage").asText());
    }

    @Test
    void onlyOneConditionalClaimCanOwnFinalization() {
        var orderRepository = mock(ProductOrderRepository.class);
        var service = new ProductOrderFinalizationService(
                orderRepository,
                mock(CallbackOutboxRepository.class),
                new CallbackOutboxProperties(),
                new ObjectMapper()
        );
        when(orderRepository.claimFinalization(
                44L, OrderStatus.IN_PROGRESS, OrderStatus.FINALIZING)).thenReturn(1, 0);

        assertTrue(service.claim(44L));
        org.junit.jupiter.api.Assertions.assertFalse(service.claim(44L));
    }

    @Test
    void resultEventIsRecordedWithTerminalStateAndOutbox() {
        var orderRepository = mock(ProductOrderRepository.class);
        var outboxRepository = mock(CallbackOutboxRepository.class);
        var processedRepository = mock(ProcessedEventRepository.class);
        var correlationId = UUID.randomUUID();
        var event = new ProductResultEvent(UUID.randomUUID(), "ProductResult", 1, correlationId,
                UUID.randomUUID(), "subscriber-service", Instant.now(),
                new ProductResult(45L, "customer-1", true, null, List.of()));
        var order = ProductOrder.builder().orderId(45L).customerId("customer-1")
                .correlationId(correlationId).crmCallbackUrl("http://crm/api/orders/callback")
                .status(OrderStatus.FINALIZING).build();
        when(orderRepository.completeFinalization(45L, 9002L,
                OrderStatus.FINALIZING, OrderStatus.COMPLETED)).thenReturn(1);
        when(orderRepository.findById(45L)).thenReturn(Optional.of(order));
        when(processedRepository.insertIfAbsent("orchestrator-product-result", event.eventId(),
                event.eventType(), event.eventVersion(), correlationId)).thenReturn(1);
        var service = new ProductOrderFinalizationService(orderRepository, outboxRepository,
                new CallbackOutboxProperties(), new ObjectMapper(), processedRepository);

        assertTrue(service.complete(45L, 9002L, event));

        verify(processedRepository).insertIfAbsent("orchestrator-product-result", event.eventId(),
                event.eventType(), event.eventVersion(), correlationId);
        verify(outboxRepository).save(org.mockito.ArgumentMatchers.argThat(
                row -> correlationId.toString().equals(row.getCorrelationId())));
    }
}

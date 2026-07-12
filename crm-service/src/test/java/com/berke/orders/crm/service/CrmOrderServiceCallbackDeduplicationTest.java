package com.berke.orders.crm.service;

import com.berke.orders.crm.config.IntegrationProperties;
import com.berke.orders.crm.dto.CrmDtos.ProductOrderCallback;
import com.berke.orders.crm.model.ProcessedCallbackEvent;
import com.berke.orders.crm.model.ProductOrder;
import com.berke.orders.crm.repo.ProcessedCallbackEventRepository;
import com.berke.orders.crm.repo.ProductOrderItemRepository;
import com.berke.orders.crm.repo.ProductOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrmOrderServiceCallbackDeduplicationTest {

    @Test
    void sequentialDuplicateReturnsSuccessAndUpdatesOrderOnce() throws Exception {
        var fixture = fixture();
        UUID eventId = UUID.randomUUID();
        when(fixture.processedRepository.insertIfAbsent(eventId, "PRODUCT_ORDER", 42L))
                .thenReturn(1, 0);
        when(fixture.processedRepository.findByEventId(eventId))
                .thenReturn(processedEvent(eventId, 42L));

        fixture.service.callback(eventId, fixture.callback);
        fixture.service.callback(eventId, fixture.callback);

        assertEquals("COMPLETED", fixture.order.getStatus());
        verify(fixture.orderRepository, times(1)).findById(42L);
        verify(fixture.orderRepository, times(1)).save(fixture.order);
        assertNotNull(CrmOrderService.class
                .getMethod("callback", UUID.class, ProductOrderCallback.class)
                .getAnnotation(Transactional.class));
    }

    @Test
    void concurrentDuplicateUpdatesOrderOnce() throws Exception {
        var fixture = fixture();
        UUID eventId = UUID.randomUUID();
        var uniqueInsertWon = new AtomicBoolean();
        when(fixture.processedRepository.insertIfAbsent(eventId, "PRODUCT_ORDER", 42L))
                .thenAnswer(ignored -> uniqueInsertWon.compareAndSet(false, true) ? 1 : 0);
        when(fixture.processedRepository.findByEventId(eventId))
                .thenReturn(processedEvent(eventId, 42L));
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                fixture.service.callback(eventId, fixture.callback);
                return null;
            });
            var second = executor.submit(() -> {
                start.await();
                fixture.service.callback(eventId, fixture.callback);
                return null;
            });
            start.countDown();

            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        }

        assertTrue(uniqueInsertWon.get());
        assertEquals("COMPLETED", fixture.order.getStatus());
        verify(fixture.orderRepository, times(1)).findById(42L);
        verify(fixture.orderRepository, times(1)).save(fixture.order);
    }

    @Test
    void reusedEventIdForDifferentOperationIsRejected() {
        var fixture = fixture();
        UUID eventId = UUID.randomUUID();
        when(fixture.processedRepository.insertIfAbsent(eventId, "PRODUCT_ORDER", 42L))
                .thenReturn(0);
        when(fixture.processedRepository.findByEventId(eventId))
                .thenReturn(processedEvent(eventId, 999L));

        assertThrows(ResponseStatusException.class,
                () -> fixture.service.callback(eventId, fixture.callback));

        verify(fixture.orderRepository, times(0)).save(fixture.order);
    }

    private Fixture fixture() {
        var orderRepository = mock(ProductOrderRepository.class);
        var itemRepository = mock(ProductOrderItemRepository.class);
        var processedRepository = mock(ProcessedCallbackEventRepository.class);
        var service = new CrmOrderService(
                orderRepository,
                itemRepository,
                processedRepository,
                new IntegrationProperties()
        );
        var order = ProductOrder.builder()
                .orderId(42L)
                .customerId("customer-1")
                .status("IN_PROGRESS")
                .build();
        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));
        return new Fixture(
                service,
                orderRepository,
                processedRepository,
                order,
                new ProductOrderCallback(42L, "COMPLETED", null)
        );
    }

    private ProcessedCallbackEvent processedEvent(UUID eventId, Long operationId) {
        return ProcessedCallbackEvent.builder()
                .id(1L)
                .eventId(eventId)
                .operationType("PRODUCT_ORDER")
                .operationId(operationId)
                .processedAt(Instant.now())
                .build();
    }

    private record Fixture(
            CrmOrderService service,
            ProductOrderRepository orderRepository,
            ProcessedCallbackEventRepository processedRepository,
            ProductOrder order,
            ProductOrderCallback callback
    ) {
    }
}

package com.berke.orders.subscriber.service;

import com.berke.orders.subscriber.dto.SubscriberDtos.*;
import com.berke.orders.subscriber.model.CustomerProduct;
import com.berke.orders.subscriber.model.ProductLifecycleStatus;
import com.berke.orders.subscriber.model.ProductOrderAction;
import com.berke.orders.subscriber.repo.CustomerProductRepository;
import com.berke.orders.subscriber.repo.CustomerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import com.berke.orders.subscriber.exception.UnsupportedEventException;

class ProductConsumerTest {
    private final CustomerRepository customerRepo = mock(CustomerRepository.class);
    private final CustomerProductRepository productRepo = mock(CustomerProductRepository.class);
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final InboxService inbox = mock(InboxService.class);
    private final ProductRemovalService productRemovalService = mock(ProductRemovalService.class);
    private final TariffChangeService tariffChangeService = mock(TariffChangeService.class);
    private final Instant activationTime = Instant.parse("2024-01-31T10:15:30Z");
    private final ProductLifecycleCalculator lifecycleCalculator =
            new ProductLifecycleCalculator(Clock.fixed(activationTime, ZoneOffset.UTC));
    private final ProductConsumer consumer = new ProductConsumer(
            customerRepo, productRepo, rabbit, inbox, lifecycleCalculator,
            productRemovalService, tariffChangeService);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publishesSuccessOnlyAfterCommitAndUsesDeterministicReference() {
        when(customerRepo.existsById("customer-1")).thenReturn(true);
        when(productRepo.findByTargetItemRef(anyString())).thenReturn(Optional.empty());
        var command = addCommand();
        TransactionSynchronizationManager.initSynchronization();
        when(inbox.begin(anyString(), any(), anyString(), anyInt(), any())).thenReturn(true);

        consumer.consume(event(command));

        verifyNoInteractions(rabbit);
        var synchronization = TransactionSynchronizationManager.getSynchronizations().getFirst();
        synchronization.afterCommit();
        verify(rabbit).convertAndSend(eq("subscriber.product.result.queue"), any(ProductResultEvent.class));

        var product = ArgumentCaptor.forClass(CustomerProduct.class);
        verify(productRepo).save(product.capture());
        assertTrue(product.getValue().getTargetItemRef().startsWith("SUBITEM-77-"));
        assertEquals(1, product.getValue().getProductVersion());
        assertEquals("FIXED_DURATION", product.getValue().getValidityType());
        assertEquals(ProductLifecycleStatus.ACTIVE, product.getValue().getStatus());
        assertEquals(77L, product.getValue().getActivationOrderId());
        assertEquals(activationTime, product.getValue().getActivatedAt());
        assertEquals(Instant.parse("2024-04-30T10:15:30Z"), product.getValue().getExpiresAt());
    }

    @Test
    void redeliveryReusesPersistedProductInsteadOfProvisioningAgain() {
        var existing = CustomerProduct.builder().customerId("customer-1").targetProductCode("TA")
                .targetItemRef("placeholder").productType("ADDON").productVersion(1)
                .validityType("FIXED_DURATION").validityAmount(3).validityUnit("MONTHS")
                .activatedAt(Instant.parse("2023-01-31T10:15:30Z"))
                .expiresAt(Instant.parse("2023-04-30T10:15:30Z"))
                .status(ProductLifecycleStatus.ACTIVE).activationOrderId(77L).build();
        when(productRepo.findByTargetItemRef(anyString())).thenReturn(Optional.of(existing));
        var command = addCommand();
        TransactionSynchronizationManager.initSynchronization();
        when(inbox.begin(anyString(), any(), anyString(), anyInt(), any())).thenReturn(true);

        consumer.consume(event(command));

        verify(productRepo, never()).save(any());
        assertEquals(Instant.parse("2023-04-30T10:15:30Z"), existing.getExpiresAt());
        TransactionSynchronizationManager.getSynchronizations().getFirst().afterCommit();
        verify(rabbit).convertAndSend(eq("subscriber.product.result.queue"), any(ProductResultEvent.class));
    }

    @Test
    void duplicateEventReplaysStoredResultWithoutProvisioningAgain() {
        UUID eventId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        var command = addCommand();
        var envelope = new ProductCommandEvent(eventId, "ProductCommand", 1, correlationId,
                UUID.randomUUID(), "orchestration-service", Instant.now(), command);
        var stored = new ProductResultEvent(UUID.randomUUID(), "ProductResult", 1, correlationId,
                eventId, "subscriber-service", Instant.now(),
                new ProductResult(77L, "customer-1", ProductOrderAction.ADD,
                        null, null, true, null, List.of()));
        when(inbox.begin(anyString(), eq(eventId), anyString(), anyInt(), eq(correlationId))).thenReturn(false);
        when(inbox.replay(anyString(), eq(eventId), eq(ProductResultEvent.class))).thenReturn(stored);
        TransactionSynchronizationManager.initSynchronization();

        consumer.consume(envelope);

        verifyNoInteractions(customerRepo, productRepo);
        TransactionSynchronizationManager.getSynchronizations().getFirst().afterCommit();
        verify(rabbit).convertAndSend("subscriber.product.result.queue", stored);
    }

    @Test
    void removeCommandUsesStandardResultFlow() {
        var command = new ProductCommand(88L, "customer-1", ProductOrderAction.REMOVE,
                41L, null, "CUSTOMER_REQUEST", List.of());
        TransactionSynchronizationManager.initSynchronization();
        when(inbox.begin(anyString(), any(), anyString(), anyInt(), any())).thenReturn(true);

        consumer.consume(event(command));

        verify(productRemovalService).remove(88L, "customer-1", 41L, "CUSTOMER_REQUEST");
        var result = ArgumentCaptor.forClass(ProductResultEvent.class);
        verify(inbox).storeResult(anyString(), any(), result.capture());
        assertEquals(2, result.getValue().eventVersion());
        assertEquals(ProductOrderAction.REMOVE, result.getValue().payload().action());
        assertEquals(41L, result.getValue().payload().productInstanceId());
        assertTrue(result.getValue().payload().success());
        assertTrue(result.getValue().payload().items().isEmpty());
        verifyNoInteractions(customerRepo, productRepo);
    }

    @Test
    void changeCommandUsesOneVersionedResultFlow() {
        var item = new ProductCommandItem("NEW", "NEW-TARIFF", "change-ref", "TARIFF",
                2, "FIXED_DURATION", 1, "YEARS");
        var command = new ProductCommand(99L, "customer-1", ProductOrderAction.CHANGE,
                null, 10L, "TARIFF_CHANGE", List.of(item));
        TransactionSynchronizationManager.initSynchronization();
        when(inbox.begin(anyString(), any(), anyString(), anyInt(), any())).thenReturn(true);

        consumer.consume(event(command));

        verify(tariffChangeService).change(eq(99L), eq("customer-1"), eq(10L),
                eq("TARIFF_CHANGE"), eq(item), startsWith("SUBITEM-99-"));
        var result = ArgumentCaptor.forClass(ProductResultEvent.class);
        verify(inbox).storeResult(anyString(), any(), result.capture());
        assertEquals(3, result.getValue().eventVersion());
        assertEquals(ProductOrderAction.CHANGE, result.getValue().payload().action());
        assertEquals(10L, result.getValue().payload().existingProductInstanceId());
        assertNull(result.getValue().payload().productInstanceId());
        assertTrue(result.getValue().payload().success());
        assertEquals(1, result.getValue().payload().items().size());
        assertEquals("TARIFF", result.getValue().payload().items().getFirst().productType());
        verifyNoInteractions(customerRepo, productRepo);
    }

    @Test
    void unsupportedVersionIsRejectedBeforeInboxInsertion() {
        var command = addCommand();
        var envelope = new ProductCommandEvent(UUID.randomUUID(), "ProductCommand", 4, UUID.randomUUID(),
                UUID.randomUUID(), "orchestration-service", Instant.now(), command);

        assertThrows(UnsupportedEventException.class, () -> consumer.consume(envelope));

        verifyNoInteractions(inbox, customerRepo, productRepo, rabbit);
    }

    @Test
    void actionVersionMismatchIsRejectedBeforeInboxInsertion() {
        var command = new ProductCommand(88L, "customer-1", ProductOrderAction.REMOVE,
                41L, null, "CUSTOMER_REQUEST", List.of());
        var envelope = new ProductCommandEvent(UUID.randomUUID(), "ProductCommand", 3, UUID.randomUUID(),
                UUID.randomUUID(), "orchestration-service", Instant.now(), command);

        assertThrows(UnsupportedEventException.class, () -> consumer.consume(envelope));

        verifyNoInteractions(inbox, customerRepo, productRepo, rabbit,
                productRemovalService, tariffChangeService);
    }

    private ProductCommandEvent event(ProductCommand command) {
        int version = switch (command.action()) {
            case ADD -> 1;
            case REMOVE -> 2;
            case CHANGE -> 3;
        };
        return new ProductCommandEvent(UUID.randomUUID(), "ProductCommand", version, UUID.randomUUID(),
                UUID.randomUUID(), "orchestration-service", Instant.now(), command);
    }

    private ProductCommand addCommand() {
        return new ProductCommand(77L, "customer-1", ProductOrderAction.ADD,
                null, null, null, List.of(addonItem()));
    }

    private ProductCommandItem addonItem() {
        return new ProductCommandItem("A", "TA", "source-1", "ADDON",
                1, "FIXED_DURATION", 3, "MONTHS");
    }
}

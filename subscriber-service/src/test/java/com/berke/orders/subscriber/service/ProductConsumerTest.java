package com.berke.orders.subscriber.service;

import com.berke.orders.subscriber.dto.SubscriberDtos.*;
import com.berke.orders.subscriber.model.CustomerProduct;
import com.berke.orders.subscriber.model.ProductLifecycleStatus;
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
    private final Instant activationTime = Instant.parse("2024-01-31T10:15:30Z");
    private final ProductLifecycleCalculator lifecycleCalculator =
            new ProductLifecycleCalculator(Clock.fixed(activationTime, ZoneOffset.UTC));
    private final ProductConsumer consumer = new ProductConsumer(
            customerRepo, productRepo, rabbit, inbox, lifecycleCalculator);

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
        var command = new ProductCommand(77L, "customer-1", List.of(
                addonItem()
        ));
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
        var command = new ProductCommand(77L, "customer-1", List.of(
                addonItem()
        ));
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
        var command = new ProductCommand(77L, "customer-1", List.of(
                addonItem()));
        var envelope = new ProductCommandEvent(eventId, "ProductCommand", 1, correlationId,
                UUID.randomUUID(), "orchestration-service", Instant.now(), command);
        var stored = new ProductResultEvent(UUID.randomUUID(), "ProductResult", 1, correlationId,
                eventId, "subscriber-service", Instant.now(),
                new ProductResult(77L, "customer-1", true, null, List.of()));
        when(inbox.begin(anyString(), eq(eventId), anyString(), anyInt(), eq(correlationId))).thenReturn(false);
        when(inbox.replay(anyString(), eq(eventId), eq(ProductResultEvent.class))).thenReturn(stored);
        TransactionSynchronizationManager.initSynchronization();

        consumer.consume(envelope);

        verifyNoInteractions(customerRepo, productRepo);
        TransactionSynchronizationManager.getSynchronizations().getFirst().afterCommit();
        verify(rabbit).convertAndSend("subscriber.product.result.queue", stored);
    }

    @Test
    void unsupportedVersionIsRejectedBeforeInboxInsertion() {
        var command = new ProductCommand(77L, "customer-1", List.of());
        var envelope = new ProductCommandEvent(UUID.randomUUID(), "ProductCommand", 2, UUID.randomUUID(),
                UUID.randomUUID(), "orchestration-service", Instant.now(), command);

        assertThrows(UnsupportedEventException.class, () -> consumer.consume(envelope));

        verifyNoInteractions(inbox, customerRepo, productRepo, rabbit);
    }

    private ProductCommandEvent event(ProductCommand command) {
        return new ProductCommandEvent(UUID.randomUUID(), "ProductCommand", 1, UUID.randomUUID(),
                UUID.randomUUID(), "orchestration-service", Instant.now(), command);
    }

    private ProductCommandItem addonItem() {
        return new ProductCommandItem("A", "TA", "source-1", "ADDON",
                1, "FIXED_DURATION", 3, "MONTHS");
    }
}

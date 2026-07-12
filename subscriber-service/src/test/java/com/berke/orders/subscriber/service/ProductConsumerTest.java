package com.berke.orders.subscriber.service;

import com.berke.orders.subscriber.dto.SubscriberDtos.*;
import com.berke.orders.subscriber.model.CustomerProduct;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import com.berke.orders.subscriber.exception.UnsupportedEventException;

class ProductConsumerTest {
    private final CustomerRepository customerRepo = mock(CustomerRepository.class);
    private final CustomerProductRepository productRepo = mock(CustomerProductRepository.class);
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final InboxService inbox = mock(InboxService.class);
    private final ProductConsumer consumer = new ProductConsumer(customerRepo, productRepo, rabbit, inbox);

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
                new ProductCommandItem("A", "TA", "source-1", "ADDON")
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
    }

    @Test
    void redeliveryReusesPersistedProductInsteadOfProvisioningAgain() {
        var existing = CustomerProduct.builder().customerId("customer-1").targetProductCode("TA")
                .targetItemRef("placeholder").productType("ADDON").active(true).build();
        when(productRepo.findByTargetItemRef(anyString())).thenReturn(Optional.of(existing));
        var command = new ProductCommand(77L, "customer-1", List.of(
                new ProductCommandItem("A", "TA", "source-1", "ADDON")
        ));
        TransactionSynchronizationManager.initSynchronization();
        when(inbox.begin(anyString(), any(), anyString(), anyInt(), any())).thenReturn(true);

        consumer.consume(event(command));

        verify(productRepo, never()).save(any());
        TransactionSynchronizationManager.getSynchronizations().getFirst().afterCommit();
        verify(rabbit).convertAndSend(eq("subscriber.product.result.queue"), any(ProductResultEvent.class));
    }

    @Test
    void duplicateEventReplaysStoredResultWithoutProvisioningAgain() {
        UUID eventId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        var command = new ProductCommand(77L, "customer-1", List.of(
                new ProductCommandItem("A", "TA", "source-1", "ADDON")));
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
}

package com.berke.orders.subscriber.service;

import com.berke.orders.subscriber.dto.SubscriberDtos.ProductCommand;
import com.berke.orders.subscriber.dto.SubscriberDtos.ProductCommandItem;
import com.berke.orders.subscriber.dto.SubscriberDtos.ProductResult;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ProductConsumerTest {
    private final CustomerRepository customerRepo = mock(CustomerRepository.class);
    private final CustomerProductRepository productRepo = mock(CustomerProductRepository.class);
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final ProductConsumer consumer = new ProductConsumer(customerRepo, productRepo, rabbit);

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

        consumer.consume(command);

        verifyNoInteractions(rabbit);
        var synchronization = TransactionSynchronizationManager.getSynchronizations().getFirst();
        synchronization.afterCommit();
        verify(rabbit).convertAndSend(eq("subscriber.product.result.queue"), any(ProductResult.class));

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

        consumer.consume(command);

        verify(productRepo, never()).save(any());
        TransactionSynchronizationManager.getSynchronizations().getFirst().afterCommit();
        verify(rabbit).convertAndSend(eq("subscriber.product.result.queue"), any(ProductResult.class));
    }
}

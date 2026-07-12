package com.berke.orders.subscriber.service;

import com.berke.orders.subscriber.model.CustomerProduct;
import com.berke.orders.subscriber.model.ProductLifecycleStatus;
import com.berke.orders.subscriber.repo.CustomerProductRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductRemovalServiceTest {
    private static final Instant ACTIVATED_AT = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant TERMINATED_AT = Instant.parse("2026-07-13T10:00:00Z");
    private final CustomerProductRepository repository = mock(CustomerProductRepository.class);
    private final ProductRemovalService service = new ProductRemovalService(
            repository, Clock.fixed(TERMINATED_AT, ZoneOffset.UTC));

    @Test
    void removesActiveAddonAndPreservesTheRow() {
        var product = activeAddon(41L);
        when(repository.findById(41L)).thenReturn(Optional.of(product));

        service.remove(9001L, "customer-1", 41L, "CUSTOMER_REQUEST");

        assertEquals(ProductLifecycleStatus.TERMINATED, product.getStatus());
        assertEquals(TERMINATED_AT, product.getTerminatedAt());
        assertEquals("CUSTOMER_REQUEST", product.getTerminationReason());
        assertEquals(9001L, product.getTerminationOrderId());
        verify(repository).saveAndFlush(product);
        verify(repository, never()).delete(any());
    }

    @Test
    void replayOfSameRemovalOrderIsIdempotent() {
        var product = activeAddon(41L);
        when(repository.findById(41L)).thenReturn(Optional.of(product));
        service.remove(9001L, "customer-1", 41L, "CUSTOMER_REQUEST");
        reset(repository);
        when(repository.findById(41L)).thenReturn(Optional.of(product));

        service.remove(9001L, "customer-1", 41L, "CUSTOMER_REQUEST");

        assertEquals(TERMINATED_AT, product.getTerminatedAt());
        assertEquals(9001L, product.getTerminationOrderId());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void missingProductInstanceFailsDeterministically() {
        when(repository.findById(404L)).thenReturn(Optional.empty());

        var error = assertThrows(IllegalArgumentException.class,
                () -> service.remove(9001L, "customer-1", 404L, "CUSTOMER_REQUEST"));

        assertEquals("Product instance not found: 404", error.getMessage());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void differentOrderCannotRemoveAlreadyTerminatedInstance() {
        var product = activeAddon(41L);
        when(repository.findById(41L)).thenReturn(Optional.of(product));
        service.remove(9001L, "customer-1", 41L, "FIRST_REQUEST");
        reset(repository);
        when(repository.findById(41L)).thenReturn(Optional.of(product));

        var error = assertThrows(IllegalArgumentException.class,
                () -> service.remove(9002L, "customer-1", 41L, "SECOND_REQUEST"));

        assertEquals("Product instance is already terminated: 41", error.getMessage());
        assertEquals(9001L, product.getTerminationOrderId());
        assertEquals("FIRST_REQUEST", product.getTerminationReason());
        verify(repository, never()).saveAndFlush(any());
    }

    private CustomerProduct activeAddon(Long id) {
        var product = CustomerProduct.builder()
                .id(id)
                .customerId("customer-1")
                .productType("ADDON")
                .status(ProductLifecycleStatus.PENDING)
                .build();
        product.activate(7001L, ACTIVATED_AT, null);
        return product;
    }
}

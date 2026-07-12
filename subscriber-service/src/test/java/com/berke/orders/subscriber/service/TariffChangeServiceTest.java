package com.berke.orders.subscriber.service;

import com.berke.orders.subscriber.dto.SubscriberDtos.ProductCommandItem;
import com.berke.orders.subscriber.model.CustomerProduct;
import com.berke.orders.subscriber.model.ProductLifecycleStatus;
import com.berke.orders.subscriber.repo.CustomerProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TariffChangeServiceTest {
    private static final Instant CHANGE_TIME = Instant.parse("2026-07-13T12:00:00Z");
    private final CustomerProductRepository repository = mock(CustomerProductRepository.class);
    private final ProductLifecycleCalculator calculator =
            new ProductLifecycleCalculator(Clock.fixed(CHANGE_TIME, ZoneOffset.UTC));
    private final TariffChangeService service =
            new TariffChangeService(repository, calculator, Clock.fixed(CHANGE_TIME, ZoneOffset.UTC));

    @Test
    void atomicallyTerminatesOldTariffBeforeActivatingReplacement() {
        var current = activeTariff(10L, "OLD-TARIFF", 7001L);
        when(repository.findByIdForUpdate(10L)).thenReturn(Optional.of(current));
        when(repository.findByTargetItemRef("replacement-ref")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(CustomerProduct.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var replacement = service.change(9001L, "customer-1", 10L, "TARIFF_CHANGE",
                replacement(), "replacement-ref");

        assertEquals(ProductLifecycleStatus.TERMINATED, current.getStatus());
        assertEquals(CHANGE_TIME, current.getTerminatedAt());
        assertEquals(9001L, current.getTerminationOrderId());
        assertEquals("TARIFF_CHANGE", current.getTerminationReason());
        assertEquals(ProductLifecycleStatus.ACTIVE, replacement.getStatus());
        assertEquals("NEW-TARIFF", replacement.getTargetProductCode());
        assertEquals(9001L, replacement.getActivationOrderId());
        assertEquals(CHANGE_TIME, replacement.getActivatedAt());
        assertEquals(Instant.parse("2027-07-13T12:00:00Z"), replacement.getExpiresAt());
        assertEquals(1, java.util.stream.Stream.of(current, replacement)
                .filter(product -> product.getStatus() == ProductLifecycleStatus.ACTIVE)
                .count());

        var writes = inOrder(repository);
        writes.verify(repository).saveAndFlush(current);
        writes.verify(repository).saveAndFlush(replacement);
        verify(repository, never()).delete(any());
    }

    @Test
    void invalidReplacementFailsBeforeCurrentTariffIsTouched() {
        var current = activeTariff(10L, "OLD-TARIFF", 7001L);
        var invalid = new ProductCommandItem("NEW", "NEW-TARIFF", "change-ref", "TARIFF",
                1, "FIXED_DURATION", null, "YEARS");

        assertThrows(IllegalArgumentException.class, () -> service.change(
                9001L, "customer-1", 10L, "TARIFF_CHANGE", invalid, "replacement-ref"));

        assertEquals(ProductLifecycleStatus.ACTIVE, current.getStatus());
        verifyNoInteractions(repository);
    }

    @Test
    void replayOfSameChangeOrderReturnsPersistedReplacement() {
        var current = activeTariff(10L, "OLD-TARIFF", 7001L);
        current.terminate(9001L, CHANGE_TIME, "TARIFF_CHANGE");
        var persistedReplacement = activeTariff(11L, "NEW-TARIFF", 9001L);
        when(repository.findByIdForUpdate(10L)).thenReturn(Optional.of(current));
        when(repository.findByTargetItemRef("replacement-ref")).thenReturn(Optional.of(persistedReplacement));

        var result = service.change(9001L, "customer-1", 10L, "TARIFF_CHANGE",
                replacement(), "replacement-ref");

        assertSame(persistedReplacement, result);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void changeMethodDefinesOneTransactionForSwitchAndRollback() throws Exception {
        var transaction = TariffChangeService.class
                .getMethod("change", Long.class, String.class, Long.class, String.class,
                        ProductCommandItem.class, String.class)
                .getAnnotation(Transactional.class);
        assertNotNull(transaction);
        assertArrayEquals(new Class<?>[]{IllegalArgumentException.class}, transaction.noRollbackFor());
    }

    private CustomerProduct activeTariff(Long id, String targetCode, Long activationOrderId) {
        var product = CustomerProduct.builder()
                .id(id)
                .customerId("customer-1")
                .targetProductCode(targetCode)
                .targetItemRef(id == 11L ? "replacement-ref" : "old-ref")
                .productType("TARIFF")
                .productVersion(1)
                .validityType("FIXED_DURATION")
                .validityAmount(1)
                .validityUnit("YEARS")
                .status(ProductLifecycleStatus.PENDING)
                .build();
        product.activate(activationOrderId, Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2027-01-01T00:00:00Z"));
        return product;
    }

    private ProductCommandItem replacement() {
        return new ProductCommandItem("NEW", "NEW-TARIFF", "change-ref", "TARIFF",
                2, "FIXED_DURATION", 1, "YEARS");
    }
}

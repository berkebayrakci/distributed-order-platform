package com.berke.orders.subscriber.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class CustomerProductLifecycleTest {
    private static final Instant ACTIVATED_AT = Instant.parse("2026-07-13T10:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-10-13T10:00:00Z");

    @Test
    void supportsActivationSuspensionResumeAndTermination() {
        var product = pending();

        product.activate(101L, ACTIVATED_AT, EXPIRES_AT);
        assertEquals(ProductLifecycleStatus.ACTIVE, product.getStatus());
        assertEquals(101L, product.getActivationOrderId());

        product.suspend();
        assertEquals(ProductLifecycleStatus.SUSPENDED, product.getStatus());

        product.resume();
        product.terminate(202L, ACTIVATED_AT.plusSeconds(3600), "CUSTOMER_REQUEST");

        assertEquals(ProductLifecycleStatus.TERMINATED, product.getStatus());
        assertEquals(202L, product.getTerminationOrderId());
        assertEquals("CUSTOMER_REQUEST", product.getTerminationReason());
        assertEquals(ACTIVATED_AT.plusSeconds(3600), product.getTerminatedAt());
    }

    @Test
    void activeOrSuspendedProductCanExpire() {
        var active = pending();
        active.activate(101L, ACTIVATED_AT, EXPIRES_AT);
        active.expire(EXPIRES_AT);
        assertEquals(ProductLifecycleStatus.EXPIRED, active.getStatus());
        assertEquals(EXPIRES_AT, active.getExpiredAt());

        var suspended = pending();
        suspended.activate(102L, ACTIVATED_AT, EXPIRES_AT);
        suspended.suspend();
        suspended.expire(EXPIRES_AT);
        assertEquals(ProductLifecycleStatus.EXPIRED, suspended.getStatus());
    }

    @Test
    void suspendedProductCannotBeTerminated() {
        var product = pending();
        product.activate(101L, ACTIVATED_AT, EXPIRES_AT);
        product.suspend();

        assertThrows(IllegalStateException.class,
                () -> product.terminate(202L, ACTIVATED_AT.plusSeconds(1), "CUSTOMER_REQUEST"));
        assertEquals(ProductLifecycleStatus.SUSPENDED, product.getStatus());
        assertNull(product.getTerminatedAt());
    }

    @Test
    void pendingProductCanBeCancelledOrFail() {
        var cancelled = pending();
        cancelled.cancel();
        assertEquals(ProductLifecycleStatus.CANCELLED, cancelled.getStatus());

        var failed = pending();
        failed.fail();
        assertEquals(ProductLifecycleStatus.FAILED, failed.getStatus());
    }

    @Test
    void terminalStatesRejectFurtherTransitions() {
        var product = pending();
        product.activate(101L, ACTIVATED_AT, EXPIRES_AT);
        product.terminate(202L, ACTIVATED_AT.plusSeconds(1), "CUSTOMER_REQUEST");

        var error = assertThrows(IllegalStateException.class, product::suspend);
        assertEquals("Cannot suspend product in status TERMINATED", error.getMessage());
        assertThrows(IllegalStateException.class,
                () -> product.terminate(203L, ACTIVATED_AT.plusSeconds(2), "DUPLICATE"));
        assertThrows(IllegalStateException.class, () -> product.expire(EXPIRES_AT));
    }

    @Test
    void transitionsRejectMissingAuditDataAndImpossibleDates() {
        assertThrows(IllegalArgumentException.class,
                () -> pending().activate(null, ACTIVATED_AT, EXPIRES_AT));
        assertThrows(IllegalArgumentException.class,
                () -> pending().activate(101L, ACTIVATED_AT, ACTIVATED_AT.minusSeconds(1)));

        var product = pending();
        product.activate(101L, ACTIVATED_AT, EXPIRES_AT);
        assertThrows(IllegalArgumentException.class,
                () -> product.terminate(202L, ACTIVATED_AT.plusSeconds(1), " "));
        assertNull(product.getTerminatedAt());
        assertNull(product.getTerminationOrderId());
        assertThrows(IllegalArgumentException.class,
                () -> product.expire(ACTIVATED_AT.minusSeconds(1)));
        assertNull(product.getExpiredAt());
        assertEquals(ProductLifecycleStatus.ACTIVE, product.getStatus());
    }

    private CustomerProduct pending() {
        return CustomerProduct.builder().status(ProductLifecycleStatus.PENDING).build();
    }
}

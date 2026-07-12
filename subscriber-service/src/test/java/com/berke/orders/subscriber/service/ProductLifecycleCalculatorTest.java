package com.berke.orders.subscriber.service;

import com.berke.orders.subscriber.dto.SubscriberDtos.ProductCommandItem;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class ProductLifecycleCalculatorTest {

    @Test
    void oneCalendarYearFromLeapDayEndsOnFebruaryTwentyEighth() {
        Instant activatedAt = Instant.parse("2024-02-29T12:00:00Z");
        var calculator = new ProductLifecycleCalculator(Clock.fixed(activatedAt, ZoneOffset.UTC));
        var tariff = item("TARIFF", 7, 1, "YEARS");

        var dates = calculator.resolve(tariff);

        assertEquals(activatedAt, dates.activatedAt());
        assertEquals(Instant.parse("2025-02-28T12:00:00Z"), dates.expiresAt());
    }

    @Test
    void threeCalendarMonthsFromJanuaryThirtyFirstEndsOnAprilThirtieth() {
        Instant activatedAt = Instant.parse("2024-01-31T23:30:00Z");
        var calculator = new ProductLifecycleCalculator(Clock.fixed(activatedAt, ZoneOffset.UTC));
        var addon = item("ADDON", 3, 3, "MONTHS");

        var dates = calculator.resolve(addon);

        assertEquals(Instant.parse("2024-04-30T23:30:00Z"), dates.expiresAt());
    }

    @Test
    void nonExpiringProductHasNoExpiry() {
        Instant activatedAt = Instant.parse("2026-07-13T00:00:00Z");
        var calculator = new ProductLifecycleCalculator(Clock.fixed(activatedAt, ZoneOffset.UTC));
        var item = new ProductCommandItem("A", "TA", "ref", "ADDON",
                2, "NON_EXPIRING", null, null);

        var dates = calculator.resolve(item);

        assertEquals(activatedAt, dates.activatedAt());
        assertNull(dates.expiresAt());
    }

    private ProductCommandItem item(String productType, int version, int amount, String unit) {
        return new ProductCommandItem("A", "TA", "ref", productType,
                version, "FIXED_DURATION", amount, unit);
    }
}

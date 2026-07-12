package com.berke.orders.subscriber.service;

import com.berke.orders.subscriber.dto.SubscriberDtos.ProductCommandItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.*;

@Service
public class ProductLifecycleCalculator {
    private final Clock clock;

    @Autowired
    public ProductLifecycleCalculator() {
        this(Clock.systemUTC());
    }

    public ProductLifecycleCalculator(Clock clock) {
        this.clock = clock;
    }

    public LifecycleDates resolve(ProductCommandItem item) {
        if (item.productVersion() == null || item.productVersion() <= 0) {
            throw new IllegalArgumentException("Product version must be positive");
        }
        Instant activatedAt = clock.instant();
        if ("NON_EXPIRING".equals(item.validityType())) {
            if (item.validityAmount() != null || item.validityUnit() != null) {
                throw new IllegalArgumentException("NON_EXPIRING product must not define duration fields");
            }
            return new LifecycleDates(activatedAt, null);
        }
        if (!"FIXED_DURATION".equals(item.validityType())
                || item.validityAmount() == null || item.validityAmount() <= 0 || item.validityUnit() == null) {
            throw new IllegalArgumentException("Invalid fixed-duration product configuration");
        }

        ZonedDateTime activatedUtc = activatedAt.atZone(ZoneOffset.UTC);
        ZonedDateTime expiresUtc = switch (item.validityUnit()) {
            case "DAYS" -> activatedUtc.plusDays(item.validityAmount());
            case "MONTHS" -> activatedUtc.plusMonths(item.validityAmount());
            case "YEARS" -> activatedUtc.plusYears(item.validityAmount());
            default -> throw new IllegalArgumentException("Unsupported validity unit: " + item.validityUnit());
        };
        return new LifecycleDates(activatedAt, expiresUtc.toInstant());
    }

    public record LifecycleDates(Instant activatedAt, Instant expiresAt) {}
}

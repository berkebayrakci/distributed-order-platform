package com.berke.orders.subscriber.dto;

import java.util.*;
import java.time.Instant;

public class SubscriberDtos {
    public record ProductCommand(Long orderId, String customerId, List<ProductCommandItem> items) {
    }

    public record ProductCommandEvent(UUID eventId, String eventType, int eventVersion, UUID correlationId,
                                      UUID causationId, String producer, Instant occurredAt, ProductCommand payload) {
    }

    public record ProductCommandItem(String sourceProductCode, String targetProductCode, String sourceItemRef,
                                     String productType, Integer productVersion, String validityType,
                                     Integer validityAmount, String validityUnit) {
    }

    public record ProductResult(Long orderId, String customerId, boolean success, String errorMessage,
                                List<ProductResultItem> items) {
    }

    public record ProductResultEvent(UUID eventId, String eventType, int eventVersion, UUID correlationId,
                                     UUID causationId, String producer, Instant occurredAt, ProductResult payload) {
    }

    public record ProductResultItem(String sourceProductCode, String targetProductCode, String sourceItemRef,
                                    String targetItemRef, String productType) {
    }

    public record CustomerCommand(Long requestId, String customerId, String firstName, String lastName) {
    }

    public record CustomerCommandEvent(UUID eventId, String eventType, int eventVersion, UUID correlationId,
                                       UUID causationId, String producer, Instant occurredAt, CustomerCommand payload) {
    }

    public record CustomerResult(Long requestId, String customerId, boolean success, String errorMessage) {
    }

    public record CustomerResultEvent(UUID eventId, String eventType, int eventVersion, UUID correlationId,
                                      UUID causationId, String producer, Instant occurredAt, CustomerResult payload) {
    }
}

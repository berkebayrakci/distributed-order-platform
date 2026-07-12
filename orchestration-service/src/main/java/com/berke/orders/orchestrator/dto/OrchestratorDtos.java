package com.berke.orders.orchestrator.dto;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

public class OrchestratorDtos {
    public record ProductRequest(@NotBlank String sourceProductCode, @NotBlank String sourceItemRef,
                                 @Pattern(regexp = "TARIFF|CAMPAIGN|ADDON") String productType) {
    }

    public record CreateProductOrderRequest(@NotBlank String customerId,
                                            @NotEmpty List<@Valid ProductRequest> products) {
    }

    public record ProductOrderResponse(Long orderId, String status) {
    }

    public record ProductLookupRequest(List<String> sourceProductCodes) {
    }

    public record ProductMapItem(String sourceProductCode, String targetProductCode, String productType,
                                 Integer productVersion, String validityType, Integer validityAmount,
                                 String validityUnit, boolean renewable, boolean stackable,
                                 boolean requiresPrimaryTariff) {
    }

    public record ProductLookupResponse(List<ProductMapItem> products) {
    }

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

    public record RuntimeMappingInsertItem(String sourceProductCode, String targetProductCode, String productType,
                                           String sourceItemRef, String targetItemRef) {
    }

    public record RuntimeMappingInsertRequest(Long operationId, List<RuntimeMappingInsertItem> items) {
    }

    public record RuntimeMappingInsertResponse(Long universalProductKey) {
    }

    public record ProductOrderCallback(Long orderId, String status, String errorMessage) {
    }

    public record CreateCustomerRequest(@NotBlank String customerId, String firstName, String lastName) {
    }

    public record CustomerRequestResponse(Long requestId, String status) {
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

    public record CustomerCallback(Long requestId, String status, String errorMessage) {
    }

    public record CustomerView(String customerId, String firstName, String lastName, String status,
                               LocalDateTime createdAt) {
    }

    public record OperationStatusResponse(
            Long operationId,
            String operationType,
            String customerId,
            String status,
            String errorMessage,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }
}

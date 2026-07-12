package com.berke.orders.orchestrator.dto;

import java.time.LocalDateTime;
import java.util.List;
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

    public record ProductMapItem(String sourceProductCode, String targetProductCode) {
    }

    public record ProductLookupResponse(List<ProductMapItem> products) {
    }

    public record ProductCommand(Long orderId, String customerId, List<ProductCommandItem> items) {
    }

    public record ProductCommandItem(String sourceProductCode, String targetProductCode, String sourceItemRef,
                                     String productType) {
    }

    public record ProductResult(Long orderId, String customerId, boolean success, String errorMessage,
                                List<ProductResultItem> items) {
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

    public record CustomerResult(Long requestId, String customerId, boolean success, String errorMessage) {
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

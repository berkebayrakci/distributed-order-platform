package com.berke.orders.crm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDateTime;
import java.util.List;

public class CrmDtos {
    public record ProductRequest(@NotBlank String sourceProductCode, @NotBlank String sourceItemRef, @NotBlank String productType) {}
    public record CreateProductOrderRequest(@NotBlank String customerId, @NotEmpty List<@Valid ProductRequest> products) {}
    public record OrchestratorProductOrderRequest(String customerId, String callbackUrl, List<ProductRequest> products) {}
    public record ProductOrderResponse(Long orderId, String status) {}
    public record ProductOrderCallback(Long orderId, String status, String errorMessage) {}

    public record CreateCustomerRequest(@NotBlank String customerId, String firstName, String lastName) {}
    public record OrchestratorCustomerRequest(String customerId, String firstName, String lastName, String callbackUrl) {}
    public record CustomerRequestResponse(Long requestId, String status) {}
    public record CustomerCallback(Long requestId, String status, String errorMessage) {}

    public record CustomerView(String customerId, String firstName, String lastName, String status, LocalDateTime createdAt) {}

    public record OperationStatusResponse(
            Long operationId,
            String operationType,
            String customerId,
            String status,
            String errorMessage,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}
}

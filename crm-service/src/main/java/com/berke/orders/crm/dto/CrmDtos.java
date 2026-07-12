package com.berke.orders.crm.dto;

import com.berke.orders.crm.model.ProductOrderAction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDateTime;
import java.util.List;

public class CrmDtos {
    public record ProductRequest(@NotBlank String sourceProductCode, @NotBlank String sourceItemRef,
                                 @Pattern(regexp = "TARIFF|CAMPAIGN|ADDON") String productType) {
    }

    public record CreateProductOrderRequest(@NotBlank String customerId,
                                            ProductOrderAction action,
                                            List<@Valid ProductRequest> products,
                                            Long productInstanceId,
                                            Long existingProductInstanceId,
                                            String newProductCode,
                                            String reason) {
        public CreateProductOrderRequest {
            if (action == null) action = ProductOrderAction.ADD;
            if (products == null) products = List.of();
        }
    }

    public record OrchestratorProductOrderRequest(String customerId, ProductOrderAction action,
                                                   List<ProductRequest> products, Long productInstanceId,
                                                   Long existingProductInstanceId, String newProductCode,
                                                   String reason) {
    }

    public record ProductOrderResponse(Long orderId, String status) {
    }

    public record ProductOrderCallback(@NotNull Long orderId,
                                       @Pattern(regexp = "COMPLETED|FAILED") String status,
                                       String errorMessage) {
    }

    public record CreateCustomerRequest(@NotBlank String customerId, String firstName, String lastName) {
    }

    public record OrchestratorCustomerRequest(String customerId, String firstName, String lastName) {
    }

    public record CustomerRequestResponse(Long requestId, String status) {
    }

    public record CustomerCallback(@NotNull Long requestId,
                                   @Pattern(regexp = "COMPLETED|FAILED") String status,
                                   String errorMessage) {
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

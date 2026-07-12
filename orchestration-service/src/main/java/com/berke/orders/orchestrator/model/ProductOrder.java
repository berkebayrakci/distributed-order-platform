package com.berke.orders.orchestrator.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "product_order")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductOrder {
    @Id
    private Long orderId;
    private String customerId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductOrderAction action;
    private Long productInstanceId;
    private Long existingProductInstanceId;
    private String newProductCode;
    private String terminationReason;
    private UUID correlationId;
    private String crmCallbackUrl;
    @Enumerated(EnumType.STRING)
    private OrderStatus status;
    @Column(columnDefinition = "text")
    private String errorMessage;
    private Long universalProductKey;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

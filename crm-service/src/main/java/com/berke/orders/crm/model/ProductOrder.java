package com.berke.orders.crm.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.*;

@Entity
@Table(name = "product_order")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductOrder {
    @Id
    private Long orderId;
    private String customerId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductOrderAction action;
    private Long productInstanceId;
    private String terminationReason;
    private String status;
    @Column(columnDefinition = "text")
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void pre() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void upd() {
        updatedAt = LocalDateTime.now();
    }
}

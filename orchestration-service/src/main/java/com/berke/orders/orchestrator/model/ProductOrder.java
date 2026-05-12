package com.berke.orders.orchestrator.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

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
    private String crmCallbackUrl;
    private String status;
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

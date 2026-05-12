package com.berke.orders.catalog.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "order_product_instance_mapping")
@IdClass(OrderProductInstanceMappingId.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderProductInstanceMapping {
    @Id
    private Long universalProductKey;

    @Column(nullable = false)
    private String sourceProductCode;

    @Column(nullable = false)
    private String targetProductCode;

    @Column(nullable = false)
    private String productType;

    @Id
    private String sourceItemRef;

    @Column(nullable = false, unique = true)
    private String targetItemRef;

    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}

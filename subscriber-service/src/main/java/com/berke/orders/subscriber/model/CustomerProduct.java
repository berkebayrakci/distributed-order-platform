package com.berke.orders.subscriber.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.*;

@Entity
@Table(name = "customer_product")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerProduct {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    String customerId;
    String targetProductCode;
    String targetItemRef;
    String productType;
    Integer productVersion;
    String validityType;
    Integer validityAmount;
    String validityUnit;
    Instant activatedAt;
    Instant expiresAt;
    Boolean active;
    LocalDateTime createdAt;

    @PrePersist
    void pre() {
        createdAt = LocalDateTime.now();
        if (active == null) active = true;
    }
}

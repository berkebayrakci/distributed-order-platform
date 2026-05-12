package com.berke.orders.crm.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.*;

@Entity
@Table(name = "customer_request")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerRequestEntity {
    @Id
    private Long requestId;
    private String customerId;
    private String firstName;
    private String lastName;
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

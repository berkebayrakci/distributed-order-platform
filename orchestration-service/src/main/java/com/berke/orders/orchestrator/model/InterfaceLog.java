package com.berke.orders.orchestrator.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "interface_log")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterfaceLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    Long operationId;
    UUID correlationId;
    String traceEventId;
    Integer stepNo;
    String interfaceName;
    String direction;
    String status;
    @Column(columnDefinition = "text")
    String requestPayload;
    @Column(columnDefinition = "text")
    String responsePayload;
    @Column(columnDefinition = "text")
    String errorMessage;
    LocalDateTime createdAt;

    @PrePersist
    void pre() {
        createdAt = LocalDateTime.now();
    }
}

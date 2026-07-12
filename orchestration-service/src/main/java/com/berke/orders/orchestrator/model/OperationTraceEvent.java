package com.berke.orders.orchestrator.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "operation_trace_event")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationTraceEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    Long operationId;
    UUID correlationId;
    String traceEventId;
    Integer stepNo;
    String description;
    LocalDateTime createdAt;

    @PrePersist
    void pre() {
        createdAt = LocalDateTime.now();
    }
}

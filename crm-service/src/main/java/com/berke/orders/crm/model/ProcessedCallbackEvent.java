package com.berke.orders.crm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_callback_event")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedCallbackEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private UUID eventId;

    @Column(nullable = false, length = 64)
    private String operationType;

    @Column(nullable = false)
    private Long operationId;

    @Column(nullable = false, insertable = false, updatable = false)
    private Instant processedAt;
}

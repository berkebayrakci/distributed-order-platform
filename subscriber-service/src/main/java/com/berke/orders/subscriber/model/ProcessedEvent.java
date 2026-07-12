package com.berke.orders.subscriber.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_event", uniqueConstraints =
        @UniqueConstraint(name = "uq_subscriber_processed_event", columnNames = {"consumer_name", "event_id"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ProcessedEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private String consumerName;
    @Column(nullable = false) private UUID eventId;
    @Column(nullable = false) private String eventType;
    @Column(nullable = false) private int eventVersion;
    @Column(nullable = false) private UUID correlationId;
    @Column(nullable = false, insertable = false, updatable = false) private Instant processedAt;
    @Column(columnDefinition = "text") private String resultJson;
}

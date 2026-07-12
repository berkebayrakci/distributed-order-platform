package com.berke.orders.subscriber.repo;

import com.berke.orders.subscriber.model.ProcessedEvent;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {
    @Modifying
    @Query(value = """
            INSERT INTO processed_event
                (consumer_name, event_id, event_type, event_version, correlation_id, processed_at)
            VALUES (:consumer, :eventId, :eventType, :eventVersion, :correlationId, CURRENT_TIMESTAMP)
            ON CONFLICT (consumer_name, event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("consumer") String consumer, @Param("eventId") UUID eventId,
                       @Param("eventType") String eventType, @Param("eventVersion") int eventVersion,
                       @Param("correlationId") UUID correlationId);

    @Modifying
    @Query("UPDATE ProcessedEvent e SET e.resultJson = :resultJson WHERE e.consumerName = :consumer AND e.eventId = :eventId")
    int storeResult(@Param("consumer") String consumer, @Param("eventId") UUID eventId,
                    @Param("resultJson") String resultJson);

    Optional<ProcessedEvent> findByConsumerNameAndEventId(String consumerName, UUID eventId);
}

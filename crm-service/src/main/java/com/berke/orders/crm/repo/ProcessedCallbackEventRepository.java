package com.berke.orders.crm.repo;

import com.berke.orders.crm.model.ProcessedCallbackEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ProcessedCallbackEventRepository extends JpaRepository<ProcessedCallbackEvent, Long> {
    @Modifying
    @Query(value = """
            INSERT INTO processed_callback_event (event_id, operation_type, operation_id, processed_at)
            VALUES (:eventId, :operationType, :operationId, CURRENT_TIMESTAMP)
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("eventId") UUID eventId,
            @Param("operationType") String operationType,
            @Param("operationId") Long operationId
    );

    ProcessedCallbackEvent findByEventId(UUID eventId);
}

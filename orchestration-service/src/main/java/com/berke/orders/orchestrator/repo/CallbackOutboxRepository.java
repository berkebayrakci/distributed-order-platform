package com.berke.orders.orchestrator.repo;

import com.berke.orders.orchestrator.model.CallbackOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CallbackOutboxRepository extends JpaRepository<CallbackOutbox, Long> {
    @Modifying
    @Query(value = """
            UPDATE callback_outbox
            SET status = 'DEAD',
                next_attempt_at = NULL,
                last_error = COALESCE(last_error, 'Processing lease expired after final attempt'),
                version = version + 1
            WHERE status = 'PROCESSING'
              AND attempt_count >= max_attempts
              AND next_attempt_at <= CURRENT_TIMESTAMP
            """, nativeQuery = true)
    int markExpiredFinalAttemptsDead();

    @Query(value = """
            SELECT *
            FROM callback_outbox
            WHERE status IN ('PENDING', 'RETRY_PENDING', 'PROCESSING')
              AND attempt_count < max_attempts
              AND next_attempt_at <= CURRENT_TIMESTAMP
            ORDER BY next_attempt_at, id
            FOR UPDATE SKIP LOCKED
            LIMIT :batchSize
            """, nativeQuery = true)
    List<CallbackOutbox> findDueForUpdate(@Param("batchSize") int batchSize);
}

package com.berke.orders.orchestrator.repo;

import com.berke.orders.orchestrator.model.OperationTraceEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface TraceEventRepository extends JpaRepository<OperationTraceEvent, Long> {
    List<OperationTraceEvent> findByOperationIdOrderByStepNoAsc(Long operationId);
}

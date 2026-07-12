package com.berke.orders.orchestrator.service;

import com.berke.orders.orchestrator.dto.OrchestratorDtos.CustomerCallback;
import com.berke.orders.orchestrator.dto.OrchestratorDtos.CustomerResultEvent;
import com.berke.orders.orchestrator.repo.CustomerRequestRepository;
import com.berke.orders.orchestrator.repo.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerResultFinalizationService {
    private final CustomerRequestRepository requestRepository;
    private final ProcessedEventRepository processedEventRepository;

    @Transactional
    public Optional<Delivery> finalizeResult(CustomerResultEvent event) {
        if (processedEventRepository.insertIfAbsent("orchestrator-customer-result", event.eventId(),
                event.eventType(), event.eventVersion(), event.correlationId()) != 1) return Optional.empty();

        var result = event.payload();
        var request = requestRepository.findById(result.requestId()).orElseThrow();
        if (!"IN_PROGRESS".equals(request.getStatus())) return Optional.empty();
        String status = result.success() ? "COMPLETED" : "FAILED";
        request.setStatus(status);
        request.setErrorMessage(result.errorMessage());
        requestRepository.save(request);
        return Optional.of(new Delivery(request.getCrmCallbackUrl(), event.correlationId(),
                new CustomerCallback(result.requestId(), status, result.errorMessage())));
    }

    public record Delivery(String callbackUrl, UUID correlationId, CustomerCallback callback) {}
}

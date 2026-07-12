package com.berke.orders.orchestrator.web;

import com.berke.orders.orchestrator.dto.OrchestratorDtos.CreateCustomerRequest;
import com.berke.orders.orchestrator.dto.OrchestratorDtos.CreateProductOrderRequest;
import com.berke.orders.orchestrator.repo.InterfaceLogRepository;
import com.berke.orders.orchestrator.repo.TraceEventRepository;
import com.berke.orders.orchestrator.service.OrchestratorService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;


@RestController
@RequestMapping("/api/orchestrator")
@RequiredArgsConstructor
public class OrchestratorController {
    private final OrchestratorService service;
    private final InterfaceLogRepository logRepo;
    private final TraceEventRepository traceRepo;

    @PostMapping("/orders")
    public Object createOrder(@Valid @RequestBody CreateProductOrderRequest request,
                              @RequestHeader("X-Correlation-Id") UUID correlationId,
                              @RequestHeader("X-Event-Id") UUID causationId) {
        return service.createOrder(request, correlationId, causationId);
    }

    @PostMapping("/customers")
    public Object createCustomer(@Valid @RequestBody CreateCustomerRequest request,
                                 @RequestHeader("X-Correlation-Id") UUID correlationId,
                                 @RequestHeader("X-Event-Id") UUID causationId) {
        return service.createCustomer(request, correlationId, causationId);
    }

    @GetMapping("/operations/{id}")
    public Object getOperation(@PathVariable Long id) {
        return service.getOperation(id);
    }

    @GetMapping("/customers/{customerId}")
    public Object getCustomer(@PathVariable String customerId) {
        return service.getCustomer(customerId);
    }

    @GetMapping("/operations/{id}/logs")
    public Object logs(@PathVariable Long id) {
        return logRepo.findByOperationIdOrderByStepNoAsc(id);
    }

    @GetMapping("/operations/{id}/trace-events")
    public Object traces(@PathVariable Long id) {
        return traceRepo.findByOperationIdOrderByStepNoAsc(id);
    }

}

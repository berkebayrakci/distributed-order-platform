package com.berke.orders.orchestrator.web;

import com.berke.orders.orchestrator.dto.OrchestratorDtos.CreateCustomerRequest;
import com.berke.orders.orchestrator.dto.OrchestratorDtos.CreateProductOrderRequest;
import com.berke.orders.orchestrator.repo.InterfaceLogRepository;
import com.berke.orders.orchestrator.repo.TraceEventRepository;
import com.berke.orders.orchestrator.service.OrchestratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/orchestrator")
@RequiredArgsConstructor
public class OrchestratorController {
    private final OrchestratorService service;
    private final InterfaceLogRepository logRepo;
    private final TraceEventRepository traceRepo;

    @PostMapping("/orders")
    public Object createOrder(@RequestBody CreateProductOrderRequest request) {
        return service.createOrder(request);
    }

    @PostMapping("/customers")
    public Object createCustomer(@RequestBody CreateCustomerRequest request) {
        return service.createCustomer(request);
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

    @PostMapping("/orders/{id}/force-complete")
    public void forceComplete(@PathVariable Long id) {
        service.forceCompleteOrder(id);
    }

    @PostMapping("/orders/{id}/abort")
    public void abort(@PathVariable Long id, @RequestBody Map<String, String> body) {
        service.abortOrder(id, body.getOrDefault("reason", "Manual abort"));
    }
}

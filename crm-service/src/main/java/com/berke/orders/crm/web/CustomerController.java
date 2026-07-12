package com.berke.orders.crm.web;

import com.berke.orders.crm.dto.CrmDtos.CreateCustomerRequest;
import com.berke.orders.crm.dto.CrmDtos.CustomerCallback;
import com.berke.orders.crm.service.CrmCustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {
    private final CrmCustomerService service;

    @PostMapping
    public Object create(@Valid @RequestBody CreateCustomerRequest request,
                         @RequestHeader(value = "X-Correlation-Id", required = false) UUID correlationId) {
        return service.create(request, correlationId == null ? UUID.randomUUID() : correlationId);
    }

    @GetMapping("/{customerId}")
    public Object getCustomer(@PathVariable String customerId) {
        return service.getCustomer(customerId);
    }

    @GetMapping("/requests/{id}")
    public Object getRequest(@PathVariable Long id) {
        return service.getRequest(id);
    }

    @PostMapping("/callback")
    public void callback(
            @RequestHeader("X-Callback-Event-Id") UUID eventId,
            @RequestHeader("X-Correlation-Id") UUID correlationId,
            @Valid @RequestBody CustomerCallback request
    ) {
        service.callback(eventId, request);
    }
}

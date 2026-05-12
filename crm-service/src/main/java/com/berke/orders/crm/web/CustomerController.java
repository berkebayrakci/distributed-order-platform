package com.berke.orders.crm.web;

import com.berke.orders.crm.dto.CrmDtos.CreateCustomerRequest;
import com.berke.orders.crm.dto.CrmDtos.CustomerCallback;
import com.berke.orders.crm.service.CrmCustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@CrossOrigin
public class CustomerController {
    private final CrmCustomerService service;

    @PostMapping
    public Object create(@Valid @RequestBody CreateCustomerRequest request) {
        return service.create(request);
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
    public void callback(@RequestBody CustomerCallback request) {
        service.callback(request);
    }
}

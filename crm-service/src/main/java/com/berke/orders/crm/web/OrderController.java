package com.berke.orders.crm.web;

import com.berke.orders.crm.dto.CrmDtos.CreateProductOrderRequest;
import com.berke.orders.crm.dto.CrmDtos.ProductOrderCallback;
import com.berke.orders.crm.service.CrmOperationService;
import com.berke.orders.crm.service.CrmOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@CrossOrigin
public class OrderController {
    private final CrmOrderService orderService;
    private final CrmOperationService operationService;

    @PostMapping
    public Object create(@Valid @RequestBody CreateProductOrderRequest request) {
        return orderService.create(request);
    }

    @GetMapping("/{id}")
    public Object get(@PathVariable Long id) {
        // Backward-compatible generic lookup: product order OR customer-create operation.
        return operationService.get(id);
    }

    @GetMapping("/{id}/local")
    public Object getLocalProductOrder(@PathVariable Long id) {
        return orderService.getLocalProductOrder(id);
    }

    @PostMapping("/callback")
    public void callback(@RequestBody ProductOrderCallback request) {
        orderService.callback(request);
    }
}

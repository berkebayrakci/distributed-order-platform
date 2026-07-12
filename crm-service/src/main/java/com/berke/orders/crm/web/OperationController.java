package com.berke.orders.crm.web;

import com.berke.orders.crm.service.CrmOperationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/operations")
@RequiredArgsConstructor
public class OperationController {
    private final CrmOperationService service;

    @GetMapping("/{id}")
    public Object get(@PathVariable Long id) {
        return service.get(id);
    }
}

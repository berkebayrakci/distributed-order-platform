package com.berke.orders.subscriber.web;

import com.berke.orders.subscriber.model.Customer;
import com.berke.orders.subscriber.repo.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/subscriber/customers")
@RequiredArgsConstructor
public class SubscriberCustomerController {
    private final CustomerRepository customerRepository;

    @GetMapping("/{customerId}")
    public CustomerView get(@PathVariable String customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found: " + customerId));

        return new CustomerView(
                customer.getCustomerId(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getStatus(),
                customer.getCreatedAt()
        );
    }

    public record CustomerView(
            String customerId,
            String firstName,
            String lastName,
            String status,
            LocalDateTime createdAt
    ) {
    }
}

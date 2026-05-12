package com.berke.orders.crm.service;

import com.berke.orders.crm.dto.CrmDtos.*;
import com.berke.orders.crm.model.CustomerRequestEntity;
import com.berke.orders.crm.repo.CustomerRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CrmCustomerService {
    private final CustomerRequestRepository repo;
    private final RestClient restClient = RestClient.builder().baseUrl("http://localhost:8084").build();

    public CustomerRequestResponse create(CreateCustomerRequest req) {
        var orchReq = new OrchestratorCustomerRequest(
                req.customerId(),
                req.firstName(),
                req.lastName(),
                "http://localhost:8081/api/customers/callback"
        );

        var res = restClient.post()
                .uri("/api/orchestrator/customers")
                .body(orchReq)
                .retrieve()
                .body(CustomerRequestResponse.class);

        repo.save(CustomerRequestEntity.builder()
                .requestId(res.requestId())
                .customerId(req.customerId())
                .firstName(req.firstName())
                .lastName(req.lastName())
                .status(res.status())
                .build());

        return res;
    }

    public void callback(CustomerCallback cb) {
        var r = repo.findById(cb.requestId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer request not found: " + cb.requestId()));
        r.setStatus(cb.status());
        r.setErrorMessage(cb.errorMessage());
        repo.save(r);
    }

    public CustomerRequestEntity getRequest(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer request not found: " + id));
    }

    public CustomerView getCustomer(String customerId) {
        try {
            return restClient.get()
                    .uri("/api/orchestrator/customers/{customerId}", customerId)
                    .retrieve()
                    .body(CustomerView.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found: " + customerId);
        }
    }
}

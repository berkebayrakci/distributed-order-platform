package com.berke.orders.crm.service;

import com.berke.orders.crm.dto.CrmDtos.*;
import com.berke.orders.crm.config.IntegrationProperties;
import com.berke.orders.crm.model.CustomerRequestEntity;
import com.berke.orders.crm.repo.CustomerRequestRepository;
import com.berke.orders.crm.repo.ProcessedCallbackEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CrmCustomerService {
    private final CustomerRequestRepository repo;
    private final ProcessedCallbackEventRepository processedCallbackRepository;
    private final IntegrationProperties integrations;
    private final RestClient restClient = RestClient.builder().build();

    @Transactional
    public CustomerRequestResponse create(CreateCustomerRequest req) {
        var orchReq = new OrchestratorCustomerRequest(
                req.customerId(),
                req.firstName(),
                req.lastName()
        );

        var res = restClient.post()
                .uri(integrations.getOrchestratorBaseUrl() + "/api/orchestrator/customers")
                .header("X-Internal-Api-Key", integrations.getInternalApiKey())
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

    @Transactional
    public void callback(UUID eventId, CustomerCallback cb) {
        if (isDuplicate(eventId, "CUSTOMER_CREATE", cb.requestId())) return;

        var r = repo.findById(cb.requestId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer request not found: " + cb.requestId()));
        if (!"IN_PROGRESS".equals(r.getStatus())) {
            if (r.getStatus().equals(cb.status())) return;
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Customer request is already terminal");
        }
        r.setStatus(cb.status());
        r.setErrorMessage(cb.errorMessage());
        repo.save(r);
    }

    private boolean isDuplicate(UUID eventId, String operationType, Long operationId) {
        if (processedCallbackRepository.insertIfAbsent(eventId, operationType, operationId) == 1) {
            return false;
        }

        var processed = processedCallbackRepository.findByEventId(eventId);
        if (processed == null
                || !operationType.equals(processed.getOperationType())
                || !operationId.equals(processed.getOperationId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Callback event ID was already used for a different operation");
        }
        return true;
    }

    public CustomerRequestEntity getRequest(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer request not found: " + id));
    }

    public CustomerView getCustomer(String customerId) {
        try {
            return restClient.get()
                    .uri(integrations.getOrchestratorBaseUrl() + "/api/orchestrator/customers/{customerId}", customerId)
                    .header("X-Internal-Api-Key", integrations.getInternalApiKey())
                    .retrieve()
                    .body(CustomerView.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found: " + customerId);
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Orchestrator is unavailable", e);
        }
    }
}

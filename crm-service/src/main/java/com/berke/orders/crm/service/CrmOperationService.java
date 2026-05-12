package com.berke.orders.crm.service;

import com.berke.orders.crm.dto.CrmDtos.OperationStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CrmOperationService {
    private final RestClient restClient = RestClient.builder().baseUrl("http://localhost:8084").build();

    public OperationStatusResponse get(Long operationId) {
        try {
            return restClient.get()
                    .uri("/api/orchestrator/operations/{operationId}", operationId)
                    .retrieve()
                    .body(OperationStatusResponse.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Operation not found: " + operationId);
        }
    }
}

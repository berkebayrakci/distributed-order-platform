package com.berke.orders.crm.service;

import com.berke.orders.crm.dto.CrmDtos.OperationStatusResponse;
import com.berke.orders.crm.config.IntegrationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CrmOperationService {
    private final IntegrationProperties integrations;
    private final RestClient restClient = RestClient.builder().build();

    public OperationStatusResponse get(Long operationId) {
        try {
            return restClient.get()
                    .uri(integrations.getOrchestratorBaseUrl() + "/api/orchestrator/operations/{operationId}", operationId)
                    .header("X-Internal-Api-Key", integrations.getInternalApiKey())
                    .retrieve()
                    .body(OperationStatusResponse.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Operation not found: " + operationId);
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Orchestrator is unavailable", e);
        }
    }
}

package com.berke.orders.orchestrator.service;

import com.berke.orders.orchestrator.config.IntegrationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CallbackClient {
    private final IntegrationProperties integrations;
    private final RestClient rest = RestClient.builder().build();

    @Retryable(retryFor = RestClientException.class, maxAttempts = 6,
            backoff = @Backoff(delay = 200, multiplier = 2.0, maxDelay = 5000))
    public void post(String url, Object body, UUID eventId) {
        rest.post()
                .uri(url)
                .header("X-Internal-Api-Key", integrations.getInternalApiKey())
                .header("X-Callback-Event-Id", eventId.toString())
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    public void postOnce(String url, Object body, UUID eventId) {
        rest.post()
                .uri(url)
                .header("X-Internal-Api-Key", integrations.getInternalApiKey())
                .header("X-Callback-Event-Id", eventId.toString())
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}

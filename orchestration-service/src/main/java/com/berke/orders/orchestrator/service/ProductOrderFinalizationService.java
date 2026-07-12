package com.berke.orders.orchestrator.service;

import com.berke.orders.orchestrator.config.CallbackOutboxProperties;
import com.berke.orders.orchestrator.dto.OrchestratorDtos.ProductOrderCallback;
import com.berke.orders.orchestrator.model.CallbackOutbox;
import com.berke.orders.orchestrator.model.CallbackOutboxStatus;
import com.berke.orders.orchestrator.model.OrderStatus;
import com.berke.orders.orchestrator.repo.CallbackOutboxRepository;
import com.berke.orders.orchestrator.repo.ProductOrderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductOrderFinalizationService {
    private static final String OPERATION_TYPE = "PRODUCT_ORDER";

    private final ProductOrderRepository orderRepository;
    private final CallbackOutboxRepository outboxRepository;
    private final CallbackOutboxProperties outboxProperties;
    private final ObjectMapper objectMapper;

    @Transactional
    public boolean claim(Long orderId) {
        return orderRepository.claimFinalization(
                orderId, OrderStatus.IN_PROGRESS, OrderStatus.FINALIZING) == 1;
    }

    @Transactional
    public boolean complete(Long orderId, Long universalProductKey) {
        var callback = new ProductOrderCallback(orderId, "COMPLETED", null);
        String payload = serialize(callback);
        if (orderRepository.completeFinalization(
                orderId,
                universalProductKey,
                OrderStatus.FINALIZING,
                OrderStatus.COMPLETED
        ) != 1) return false;

        var order = orderRepository.findById(orderId).orElseThrow();
        enqueue(orderId, order.getCrmCallbackUrl(), payload);
        return true;
    }

    @Transactional
    public boolean fail(Long orderId, String error) {
        var callback = new ProductOrderCallback(orderId, "FAILED", error);
        String payload = serialize(callback);
        if (orderRepository.failActiveOrder(
                orderId,
                error,
                OrderStatus.IN_PROGRESS,
                OrderStatus.FINALIZING,
                OrderStatus.FAILED
        ) != 1) return false;

        var order = orderRepository.findById(orderId).orElseThrow();
        enqueue(orderId, order.getCrmCallbackUrl(), payload);
        return true;
    }

    private void enqueue(Long orderId, String callbackUrl, String payload) {
        Instant now = Instant.now();
        outboxRepository.save(CallbackOutbox.builder()
                .eventId(UUID.randomUUID())
                .correlationId(orderId.toString())
                .operationType(OPERATION_TYPE)
                .operationId(orderId)
                .callbackUrl(callbackUrl)
                .httpMethod("POST")
                .payloadJson(payload)
                .status(CallbackOutboxStatus.PENDING)
                .attemptCount(0)
                .maxAttempts(outboxProperties.getMaxAttempts())
                .nextAttemptAt(now)
                .createdAt(now)
                .build());
    }

    private String serialize(ProductOrderCallback callback) {
        try {
            return objectMapper.writeValueAsString(callback);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize CRM callback for order " + callback.orderId(), e);
        }
    }
}

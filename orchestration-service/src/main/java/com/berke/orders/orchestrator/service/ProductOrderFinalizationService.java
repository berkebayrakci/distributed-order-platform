package com.berke.orders.orchestrator.service;

import com.berke.orders.orchestrator.config.CallbackOutboxProperties;
import com.berke.orders.orchestrator.dto.OrchestratorDtos.ProductOrderCallback;
import com.berke.orders.orchestrator.model.CallbackOutbox;
import com.berke.orders.orchestrator.model.CallbackOutboxStatus;
import com.berke.orders.orchestrator.model.OrderStatus;
import com.berke.orders.orchestrator.repo.CallbackOutboxRepository;
import com.berke.orders.orchestrator.repo.ProductOrderRepository;
import com.berke.orders.orchestrator.repo.ProcessedEventRepository;
import com.berke.orders.orchestrator.dto.OrchestratorDtos.ProductResultEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class ProductOrderFinalizationService {
    private static final String OPERATION_TYPE = "PRODUCT_ORDER";

    private final ProductOrderRepository orderRepository;
    private final CallbackOutboxRepository outboxRepository;
    private final CallbackOutboxProperties outboxProperties;
    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository processedEventRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public ProductOrderFinalizationService(ProductOrderRepository orderRepository,
                                           CallbackOutboxRepository outboxRepository,
                                           CallbackOutboxProperties outboxProperties,
                                           ObjectMapper objectMapper,
                                           ProcessedEventRepository processedEventRepository) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.outboxProperties = outboxProperties;
        this.objectMapper = objectMapper;
        this.processedEventRepository = processedEventRepository;
    }

    public ProductOrderFinalizationService(ProductOrderRepository orderRepository,
                                           CallbackOutboxRepository outboxRepository,
                                           CallbackOutboxProperties outboxProperties,
                                           ObjectMapper objectMapper) {
        this(orderRepository, outboxRepository, outboxProperties, objectMapper, null);
    }

    @Transactional
    public boolean claim(Long orderId) {
        return orderRepository.claimFinalization(
                orderId, OrderStatus.IN_PROGRESS, OrderStatus.FINALIZING) == 1;
    }

    @Transactional
    public boolean complete(Long orderId, Long universalProductKey) {
        return complete(orderId, universalProductKey, null);
    }

    @Transactional
    public boolean complete(Long orderId, Long universalProductKey, ProductResultEvent event) {
        var callback = new ProductOrderCallback(orderId, "COMPLETED", null);
        String payload = serialize(callback);
        if (orderRepository.completeFinalization(
                orderId,
                universalProductKey,
                OrderStatus.FINALIZING,
                OrderStatus.COMPLETED
        ) != 1) return false;

        var order = orderRepository.findById(orderId).orElseThrow();
        recordResult(event);
        enqueue(orderId, order.getCorrelationId(), order.getCrmCallbackUrl(), payload);
        return true;
    }

    @Transactional
    public boolean fail(Long orderId, String error) {
        return failResult(orderId, error, null);
    }

    @Transactional
    public boolean failResult(Long orderId, String error, ProductResultEvent event) {
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
        recordResult(event);
        enqueue(orderId, order.getCorrelationId(), order.getCrmCallbackUrl(), payload);
        return true;
    }

    private void recordResult(ProductResultEvent event) {
        if (event == null) return;
        if (processedEventRepository.insertIfAbsent("orchestrator-product-result", event.eventId(),
                event.eventType(), event.eventVersion(), event.correlationId()) != 1) {
            throw new IllegalStateException("Product result event was already processed: " + event.eventId());
        }
    }

    private void enqueue(Long orderId, UUID correlationId, String callbackUrl, String payload) {
        Instant now = Instant.now();
        outboxRepository.save(CallbackOutbox.builder()
                .eventId(UUID.randomUUID())
                .correlationId(correlationId.toString())
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

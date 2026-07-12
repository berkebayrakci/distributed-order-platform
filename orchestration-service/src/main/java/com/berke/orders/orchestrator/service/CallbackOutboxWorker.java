package com.berke.orders.orchestrator.service;

import com.berke.orders.orchestrator.dto.OrchestratorDtos.ProductOrderCallback;
import com.berke.orders.orchestrator.model.CallbackOutboxStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CallbackOutboxWorker {
    private final CallbackOutboxDeliveryService deliveryService;
    private final CallbackClient callbackClient;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${callback-outbox.poll-interval:1s}")
    public void deliverDueCallbacks() {
        for (var claim : deliveryService.claimDue()) {
            deliver(claim);
        }
    }

    private void deliver(CallbackOutboxDeliveryService.Claim claim) {
        try {
            Object callback = deserialize(claim);
            callbackClient.postOnce(claim.callbackUrl(), callback, claim.eventId(), UUID.fromString(claim.correlationId()));
            if (deliveryService.markDelivered(claim)) {
                log.info("CRM callback delivered: eventId={}, operationType={}, operationId={}, attempt={}",
                        claim.eventId(), claim.operationType(), claim.operationId(), claim.attemptCount());
            }
        } catch (Exception failure) {
            CallbackOutboxStatus status = deliveryService.recordFailure(claim, failure);
            if (status == CallbackOutboxStatus.DEAD) {
                log.error("CRM callback is DEAD: eventId={}, operationType={}, operationId={}, attempts={}",
                        claim.eventId(), claim.operationType(), claim.operationId(), claim.attemptCount(), failure);
            } else if (status == CallbackOutboxStatus.RETRY_PENDING) {
                log.warn("CRM callback scheduled for retry: eventId={}, operationType={}, operationId={}, attempt={}",
                        claim.eventId(), claim.operationType(), claim.operationId(), claim.attemptCount(), failure);
            } else {
                log.warn("CRM callback claim became stale: eventId={}, operationType={}, operationId={}",
                        claim.eventId(), claim.operationType(), claim.operationId());
            }
        }
    }

    private Object deserialize(CallbackOutboxDeliveryService.Claim claim) throws Exception {
        if ("PRODUCT_ORDER".equals(claim.operationType())) {
            return objectMapper.readValue(claim.payloadJson(), ProductOrderCallback.class);
        }
        throw new IllegalArgumentException("Unsupported callback operation type: " + claim.operationType());
    }
}

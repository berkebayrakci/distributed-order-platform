package com.berke.orders.subscriber.service;

import com.berke.orders.subscriber.dto.SubscriberDtos.*;
import com.berke.orders.subscriber.model.CustomerProduct;
import com.berke.orders.subscriber.model.ProductLifecycleStatus;
import com.berke.orders.subscriber.model.ProductOrderAction;
import com.berke.orders.subscriber.repo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import com.berke.orders.subscriber.exception.UnsupportedEventException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductConsumer {
    private final CustomerRepository customerRepo;
    private final CustomerProductRepository productRepo;
    private final RabbitTemplate rabbit;
    private final InboxService inbox;
    private final ProductLifecycleCalculator lifecycleCalculator;
    private final ProductRemovalService productRemovalService;
    private final TariffChangeService tariffChangeService;
    private static final String CONSUMER = "subscriber-product-command";

    @RabbitListener(queues = "subscriber.product.command.queue")
    @Transactional
    public void consume(ProductCommandEvent event) {
        validateEnvelope(event);
        ProductCommand cmd = event.payload();
        ProductOrderAction action = commandAction(cmd, event.eventVersion());
        validateActionVersion(action, event.eventVersion());
        log.info("Subscriber service received product order {}", cmd.orderId());
        if (!inbox.begin(CONSUMER, event.eventId(), event.eventType(), event.eventVersion(), event.correlationId())) {
            publishAfterCommit(inbox.replay(CONSUMER, event.eventId(), ProductResultEvent.class));
            return;
        }
        try {
            if (action == ProductOrderAction.REMOVE) {
                productRemovalService.remove(cmd.orderId(), cmd.customerId(), cmd.productInstanceId(), cmd.reason());
                finish(event, new ProductResult(cmd.orderId(), cmd.customerId(), action,
                        cmd.productInstanceId(), null, true, null, List.of()));
                return;
            }
            if (action == ProductOrderAction.CHANGE) {
                if (cmd.items() == null || cmd.items().size() != 1) {
                    throw new IllegalArgumentException("CHANGE requires exactly one replacement tariff item");
                }
                var item = cmd.items().getFirst();
                String targetRef = targetRef(cmd.orderId(), item.sourceItemRef());
                tariffChangeService.change(cmd.orderId(), cmd.customerId(), cmd.existingProductInstanceId(),
                        cmd.reason(), item, targetRef);
                var resultItem = new ProductResultItem(item.sourceProductCode(), item.targetProductCode(),
                        item.sourceItemRef(), targetRef, item.productType());
                finish(event, new ProductResult(cmd.orderId(), cmd.customerId(), action,
                        null, cmd.existingProductInstanceId(), true, null, List.of(resultItem)));
                return;
            }
            List<ProductResultItem> replay = replayResult(cmd);
            if (replay != null) {
                finish(event, new ProductResult(cmd.orderId(), cmd.customerId(), action,
                        null, null, true, null, replay));
                return;
            }
            validate(cmd);
            List<ProductResultItem> result = new ArrayList<>();
            for (var item : cmd.items()) {
                String targetRef = targetRef(cmd.orderId(), item.sourceItemRef());
                var lifecycle = lifecycleCalculator.resolve(item);
                var product = CustomerProduct.builder()
                        .customerId(cmd.customerId())
                        .targetProductCode(item.targetProductCode())
                        .targetItemRef(targetRef)
                        .productType(item.productType())
                        .productVersion(item.productVersion())
                        .validityType(item.validityType())
                        .validityAmount(item.validityAmount())
                        .validityUnit(item.validityUnit())
                        .status(ProductLifecycleStatus.PENDING)
                        .build();
                product.activate(cmd.orderId(), lifecycle.activatedAt(), lifecycle.expiresAt());
                productRepo.save(product);
                result.add(new ProductResultItem(item.sourceProductCode(), item.targetProductCode(), item.sourceItemRef(), targetRef, item.productType()));
            }
            productRepo.flush();
            finish(event, new ProductResult(cmd.orderId(), cmd.customerId(), action,
                    null, null, true, null, result));
        } catch (IllegalArgumentException e) {
            log.error("Subscriber product order failed {}", cmd.orderId(), e);
            finish(event, new ProductResult(cmd.orderId(), cmd.customerId(),
                    action, cmd.productInstanceId(),
                    cmd.existingProductInstanceId(), false, e.getMessage(), List.of()));
        }
    }

    private List<ProductResultItem> replayResult(ProductCommand cmd) {
        List<ProductResultItem> result = new ArrayList<>();
        int existingCount = 0;
        for (var item : cmd.items()) {
            String targetRef = targetRef(cmd.orderId(), item.sourceItemRef());
            var existing = productRepo.findByTargetItemRef(targetRef);
            if (existing.isPresent()) {
                existingCount++;
                result.add(new ProductResultItem(item.sourceProductCode(), item.targetProductCode(), item.sourceItemRef(), targetRef, item.productType()));
            }
        }
        if (existingCount == 0) return null;
        if (existingCount != cmd.items().size()) return failPartialReplay(cmd.orderId());
        return result;
    }

    private List<ProductResultItem> failPartialReplay(Long orderId) {
        throw new IllegalStateException("Partial persisted state detected for order " + orderId);
    }

    private String targetRef(Long orderId, String sourceItemRef) {
        UUID id = UUID.nameUUIDFromBytes((orderId + ":" + sourceItemRef).getBytes(StandardCharsets.UTF_8));
        return "SUBITEM-" + orderId + "-" + id;
    }

    private void finish(ProductCommandEvent command, ProductResult result) {
        var event = new ProductResultEvent(UUID.randomUUID(), "ProductResult", command.eventVersion(), command.correlationId(),
                command.eventId(), "subscriber-service", Instant.now(), result);
        inbox.storeResult(CONSUMER, command.eventId(), event);
        publishAfterCommit(event);
    }

    private void publishAfterCommit(ProductResultEvent result) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                rabbit.convertAndSend("subscriber.product.result.queue", result);
            }
        });
    }

    private void validateEnvelope(ProductCommandEvent event) {
        if (event == null || event.eventId() == null || event.correlationId() == null || event.causationId() == null
                || event.occurredAt() == null || event.payload() == null || !"ProductCommand".equals(event.eventType())) {
            throw new UnsupportedEventException("Malformed or unsupported ProductCommand envelope");
        }
        if (event.eventVersion() != 1 && event.eventVersion() != 2 && event.eventVersion() != 3) {
            throw new UnsupportedEventException("Unsupported ProductCommand version: " + event.eventVersion());
        }
    }

    private ProductOrderAction commandAction(ProductCommand command, int eventVersion) {
        if (command.action() != null) return command.action();
        if (eventVersion == 1) return ProductOrderAction.ADD;
        throw new UnsupportedEventException("Product command has no order action");
    }

    private void validateActionVersion(ProductOrderAction action, int eventVersion) {
        boolean valid = (eventVersion == 1 && action == ProductOrderAction.ADD)
                || (eventVersion == 2 && action == ProductOrderAction.REMOVE)
                || (eventVersion == 3 && action == ProductOrderAction.CHANGE);
        if (!valid) {
            throw new UnsupportedEventException(
                    "Product command action " + action + " is invalid for version " + eventVersion);
        }
    }

    private void validate(ProductCommand cmd) {
        if (!customerRepo.existsById(cmd.customerId()))
            throw new IllegalArgumentException("Subscriber validation failed: customer does not exist: " + cmd.customerId());
        long t = cmd.items().stream().filter(i -> i.productType().equals("TARIFF")).count();
        long c = cmd.items().stream().filter(i -> i.productType().equals("CAMPAIGN")).count();
        if (t > 1) throw new IllegalArgumentException("Subscriber validation failed: only 1 tariff per order");
        if (c > 1) throw new IllegalArgumentException("Subscriber validation failed: only 1 campaign per order");
        if (t == 1 && productRepo.existsByCustomerIdAndProductTypeAndStatus(
                cmd.customerId(), "TARIFF", ProductLifecycleStatus.ACTIVE))
            throw new IllegalArgumentException("Subscriber validation failed: customer already has active tariff");
        if (c == 1 && productRepo.existsByCustomerIdAndProductTypeAndStatus(
                cmd.customerId(), "CAMPAIGN", ProductLifecycleStatus.ACTIVE))
            throw new IllegalArgumentException("Subscriber validation failed: customer already has active campaign");
    }
}

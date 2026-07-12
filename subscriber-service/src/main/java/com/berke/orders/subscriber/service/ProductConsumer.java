package com.berke.orders.subscriber.service;

import com.berke.orders.subscriber.dto.SubscriberDtos.*;
import com.berke.orders.subscriber.model.CustomerProduct;
import com.berke.orders.subscriber.repo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.nio.charset.StandardCharsets;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductConsumer {
    private final CustomerRepository customerRepo;
    private final CustomerProductRepository productRepo;
    private final RabbitTemplate rabbit;

    @RabbitListener(queues = "subscriber.product.command.queue")
    @Transactional
    public void consume(ProductCommand cmd) {
        log.info("Subscriber service received product order {}", cmd.orderId());
        try {
            List<ProductResultItem> replay = replayResult(cmd);
            if (replay != null) {
                publishAfterCommit(new ProductResult(cmd.orderId(), cmd.customerId(), true, null, replay));
                return;
            }
            validate(cmd);
            List<ProductResultItem> result = new ArrayList<>();
            for (var item : cmd.items()) {
                String targetRef = targetRef(cmd.orderId(), item.sourceItemRef());
                productRepo.save(CustomerProduct.builder().customerId(cmd.customerId()).targetProductCode(item.targetProductCode()).targetItemRef(targetRef).productType(item.productType()).active(true).build());
                result.add(new ProductResultItem(item.sourceProductCode(), item.targetProductCode(), item.sourceItemRef(), targetRef, item.productType()));
            }
            productRepo.flush();
            publishAfterCommit(new ProductResult(cmd.orderId(), cmd.customerId(), true, null, result));
        } catch (IllegalArgumentException e) {
            log.error("Subscriber product order failed {}", cmd.orderId(), e);
            publishAfterCommit(new ProductResult(cmd.orderId(), cmd.customerId(), false, e.getMessage(), List.of()));
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

    private void publishAfterCommit(ProductResult result) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                rabbit.convertAndSend("subscriber.product.result.queue", result);
            }
        });
    }

    private void validate(ProductCommand cmd) {
        if (!customerRepo.existsById(cmd.customerId()))
            throw new IllegalArgumentException("Subscriber validation failed: customer does not exist: " + cmd.customerId());
        long t = cmd.items().stream().filter(i -> i.productType().equals("TARIFF")).count();
        long c = cmd.items().stream().filter(i -> i.productType().equals("CAMPAIGN")).count();
        if (t > 1) throw new IllegalArgumentException("Subscriber validation failed: only 1 tariff per order");
        if (c > 1) throw new IllegalArgumentException("Subscriber validation failed: only 1 campaign per order");
        if (t == 1 && productRepo.existsByCustomerIdAndProductTypeAndActiveTrue(cmd.customerId(), "TARIFF"))
            throw new IllegalArgumentException("Subscriber validation failed: customer already has active tariff");
        if (c == 1 && productRepo.existsByCustomerIdAndProductTypeAndActiveTrue(cmd.customerId(), "CAMPAIGN"))
            throw new IllegalArgumentException("Subscriber validation failed: customer already has active campaign");
    }
}

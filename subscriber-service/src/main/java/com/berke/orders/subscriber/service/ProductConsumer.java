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
            validate(cmd);
            List<ProductResultItem> result = new ArrayList<>();
            for (var item : cmd.items()) {
                String targetRef = "SUBITEM-" + cmd.orderId() + "-" + UUID.randomUUID().toString().substring(0, 8);
                productRepo.save(CustomerProduct.builder().customerId(cmd.customerId()).targetProductCode(item.targetProductCode()).targetItemRef(targetRef).productType(item.productType()).active(true).build());
                result.add(new ProductResultItem(item.sourceProductCode(), item.targetProductCode(), item.sourceItemRef(), targetRef, item.productType()));
            }
            rabbit.convertAndSend("subscriber.product.result.queue", new ProductResult(cmd.orderId(), cmd.customerId(), true, null, result));
        } catch (Exception e) {
            log.error("Subscriber product order failed {}", cmd.orderId(), e);
            rabbit.convertAndSend("subscriber.product.result.queue", new ProductResult(cmd.orderId(), cmd.customerId(), false, e.getMessage(), List.of()));
        }
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

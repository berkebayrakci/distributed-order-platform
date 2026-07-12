package com.berke.orders.subscriber.service;

import com.berke.orders.subscriber.model.ProductLifecycleStatus;
import com.berke.orders.subscriber.repo.CustomerProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class ProductRemovalService {
    private final CustomerProductRepository productRepository;
    private final Clock clock;

    @Autowired
    public ProductRemovalService(CustomerProductRepository productRepository) {
        this(productRepository, Clock.systemUTC());
    }

    ProductRemovalService(CustomerProductRepository productRepository, Clock clock) {
        this.productRepository = productRepository;
        this.clock = clock;
    }

    @Transactional
    public void remove(Long orderId, String customerId, Long productInstanceId, String reason) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("Removal order ID must be positive");
        }
        if (productInstanceId == null || productInstanceId <= 0) {
            throw new IllegalArgumentException("REMOVE requires a positive product instance ID");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("REMOVE requires a termination reason");
        }

        var product = productRepository.findById(productInstanceId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Product instance not found: " + productInstanceId));
        if (!product.getCustomerId().equals(customerId)) {
            throw new IllegalArgumentException("Product instance does not belong to customer: " + customerId);
        }
        if (!"ADDON".equals(product.getProductType())) {
            throw new IllegalArgumentException("Normal REMOVE supports add-on product instances only");
        }
        if (product.getStatus() == ProductLifecycleStatus.TERMINATED) {
            if (orderId.equals(product.getTerminationOrderId())) return;
            throw new IllegalArgumentException("Product instance is already terminated: " + productInstanceId);
        }
        if (product.getStatus() != ProductLifecycleStatus.ACTIVE) {
            throw new IllegalArgumentException("Only an ACTIVE product instance may be removed: " + productInstanceId);
        }

        product.terminate(orderId, clock.instant(), reason);
        productRepository.saveAndFlush(product);
    }
}

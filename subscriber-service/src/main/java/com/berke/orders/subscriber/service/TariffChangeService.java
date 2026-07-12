package com.berke.orders.subscriber.service;

import com.berke.orders.subscriber.dto.SubscriberDtos.ProductCommandItem;
import com.berke.orders.subscriber.model.CustomerProduct;
import com.berke.orders.subscriber.model.ProductLifecycleStatus;
import com.berke.orders.subscriber.repo.CustomerProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class TariffChangeService {
    private final CustomerProductRepository productRepository;
    private final ProductLifecycleCalculator lifecycleCalculator;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public TariffChangeService(CustomerProductRepository productRepository,
                               ProductLifecycleCalculator lifecycleCalculator) {
        this(productRepository, lifecycleCalculator, Clock.systemUTC());
    }

    TariffChangeService(CustomerProductRepository productRepository,
                        ProductLifecycleCalculator lifecycleCalculator, Clock clock) {
        this.productRepository = productRepository;
        this.lifecycleCalculator = lifecycleCalculator;
        this.clock = clock;
    }

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public CustomerProduct change(Long orderId, String customerId, Long existingProductInstanceId,
                                  String reason, ProductCommandItem replacement, String targetItemRef) {
        validateRequest(orderId, existingProductInstanceId, reason, replacement, targetItemRef);

        // Resolve and validate the replacement before touching the current tariff.
        var lifecycle = lifecycleCalculator.resolve(replacement);
        var current = productRepository.findByIdForUpdate(existingProductInstanceId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Existing tariff instance not found: " + existingProductInstanceId));
        var priorReplacement = productRepository.findByTargetItemRef(targetItemRef);

        if (current.getStatus() == ProductLifecycleStatus.TERMINATED
                && orderId.equals(current.getTerminationOrderId())) {
            return validateReplay(orderId, replacement, targetItemRef, priorReplacement.orElseThrow(() ->
                    new IllegalStateException("Tariff change has terminated the old tariff but no replacement exists")));
        }
        if (priorReplacement.isPresent()) {
            throw new IllegalStateException("Replacement tariff reference already exists for another state");
        }
        if (!customerId.equals(current.getCustomerId())) {
            throw new IllegalArgumentException("Existing tariff does not belong to customer: " + customerId);
        }
        if (!"TARIFF".equals(current.getProductType())) {
            throw new IllegalArgumentException("Existing product instance is not a tariff: " + existingProductInstanceId);
        }
        if (current.getStatus() != ProductLifecycleStatus.ACTIVE) {
            throw new IllegalArgumentException("Only an ACTIVE tariff may be changed: " + existingProductInstanceId);
        }
        if (current.getTargetProductCode().equals(replacement.targetProductCode())) {
            throw new IllegalArgumentException("Replacement tariff must differ from the active tariff");
        }

        var next = CustomerProduct.builder()
                .customerId(customerId)
                .targetProductCode(replacement.targetProductCode())
                .targetItemRef(targetItemRef)
                .productType("TARIFF")
                .productVersion(replacement.productVersion())
                .validityType(replacement.validityType())
                .validityAmount(replacement.validityAmount())
                .validityUnit(replacement.validityUnit())
                .status(ProductLifecycleStatus.PENDING)
                .build();
        next.activate(orderId, lifecycle.activatedAt(), lifecycle.expiresAt());

        // Flush the termination first to release the partial unique-index slot. If inserting the
        // replacement fails, @Transactional rolls this update back with the insert.
        current.terminate(orderId, clock.instant(), reason);
        productRepository.saveAndFlush(current);
        return productRepository.saveAndFlush(next);
    }

    private void validateRequest(Long orderId, Long existingProductInstanceId, String reason,
                                 ProductCommandItem replacement, String targetItemRef) {
        if (orderId == null || orderId <= 0) throw new IllegalArgumentException("Change order ID must be positive");
        if (existingProductInstanceId == null || existingProductInstanceId <= 0) {
            throw new IllegalArgumentException("CHANGE requires a positive existing tariff instance ID");
        }
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("CHANGE requires a reason");
        if (replacement == null || !"TARIFF".equals(replacement.productType())) {
            throw new IllegalArgumentException("CHANGE requires exactly one replacement tariff");
        }
        if (targetItemRef == null || targetItemRef.isBlank()) {
            throw new IllegalArgumentException("CHANGE requires a target item reference");
        }
    }

    private CustomerProduct validateReplay(Long orderId, ProductCommandItem replacement,
                                           String targetItemRef, CustomerProduct persisted) {
        if (persisted.getStatus() != ProductLifecycleStatus.ACTIVE
                || !orderId.equals(persisted.getActivationOrderId())
                || !"TARIFF".equals(persisted.getProductType())
                || !replacement.targetProductCode().equals(persisted.getTargetProductCode())
                || !targetItemRef.equals(persisted.getTargetItemRef())) {
            throw new IllegalStateException("Persisted replacement does not match tariff change order " + orderId);
        }
        return persisted;
    }
}

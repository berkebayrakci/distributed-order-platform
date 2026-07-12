package com.berke.orders.subscriber.repo;

import com.berke.orders.subscriber.model.CustomerProduct;
import com.berke.orders.subscriber.model.ProductLifecycleStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerProductRepository extends JpaRepository<CustomerProduct, Long> {
    boolean existsByCustomerIdAndProductTypeAndStatus(
            String customerId, String productType, ProductLifecycleStatus status);
    Optional<CustomerProduct> findByTargetItemRef(String targetItemRef);
}

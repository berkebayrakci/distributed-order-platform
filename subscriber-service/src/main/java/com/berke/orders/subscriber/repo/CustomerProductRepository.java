package com.berke.orders.subscriber.repo;

import com.berke.orders.subscriber.model.CustomerProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerProductRepository extends JpaRepository<CustomerProduct, Long> {
    boolean existsByCustomerIdAndProductTypeAndActiveTrue(String customerId, String productType);
    Optional<CustomerProduct> findByTargetItemRef(String targetItemRef);
}

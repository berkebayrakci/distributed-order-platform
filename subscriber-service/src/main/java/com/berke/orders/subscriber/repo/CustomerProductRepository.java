package com.berke.orders.subscriber.repo;

import com.berke.orders.subscriber.model.CustomerProduct;
import com.berke.orders.subscriber.model.ProductLifecycleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface CustomerProductRepository extends JpaRepository<CustomerProduct, Long> {
    boolean existsByCustomerIdAndProductTypeAndStatus(
            String customerId, String productType, ProductLifecycleStatus status);
    Optional<CustomerProduct> findByTargetItemRef(String targetItemRef);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT product FROM CustomerProduct product WHERE product.id = :id")
    Optional<CustomerProduct> findByIdForUpdate(@Param("id") Long id);
}

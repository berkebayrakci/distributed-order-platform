package com.berke.orders.catalog.repo;

import com.berke.orders.catalog.model.OrderProductInstanceMapping;
import com.berke.orders.catalog.model.OrderProductInstanceMappingId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InstanceMappingRepository extends JpaRepository<OrderProductInstanceMapping, OrderProductInstanceMappingId> {
    List<OrderProductInstanceMapping> findByUniversalProductKeyOrderBySourceItemRef(Long universalProductKey);
}

package com.berke.orders.orchestrator.repo;

import com.berke.orders.orchestrator.model.CustomerRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRequestRepository extends JpaRepository<CustomerRequestEntity, Long> {
}

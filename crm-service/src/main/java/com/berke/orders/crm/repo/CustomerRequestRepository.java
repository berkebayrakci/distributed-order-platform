package com.berke.orders.crm.repo;

import com.berke.orders.crm.model.CustomerRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRequestRepository extends JpaRepository<CustomerRequestEntity, Long> {
}

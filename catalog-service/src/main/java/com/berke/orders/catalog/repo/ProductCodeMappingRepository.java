package com.berke.orders.catalog.repo;

import com.berke.orders.catalog.model.ProductCodeMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductCodeMappingRepository extends JpaRepository<ProductCodeMapping, String> {
    List<ProductCodeMapping> findBySourceProductCodeIn(List<String> codes);
}

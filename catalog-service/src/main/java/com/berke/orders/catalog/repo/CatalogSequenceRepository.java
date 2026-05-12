package com.berke.orders.catalog.repo;

import com.berke.orders.catalog.model.ProductCodeMapping;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface CatalogSequenceRepository extends Repository<ProductCodeMapping, String> {
    @Query(value = "select nextval('catalog.universal_product_key_seq')", nativeQuery = true)
    Long nextUniversalProductKey();
}

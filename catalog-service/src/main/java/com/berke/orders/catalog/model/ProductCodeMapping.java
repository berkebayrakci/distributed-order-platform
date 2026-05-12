package com.berke.orders.catalog.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "product_code_mapping")
@Getter
@Setter
public class ProductCodeMapping {
    @Id
    @Column(name = "source_product_code")
    private String sourceProductCode;

    @Column(name = "target_product_code", nullable = false, unique = true)
    private String targetProductCode;
}

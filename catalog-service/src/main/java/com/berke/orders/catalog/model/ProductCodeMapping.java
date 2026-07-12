package com.berke.orders.catalog.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

@Entity
@Table(name = "product_code_mapping")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductCodeMapping {
    @Id
    @Column(name = "source_product_code")
    private String sourceProductCode;

    @Column(name = "target_product_code", nullable = false, unique = true)
    private String targetProductCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductType productType;

    @Column(nullable = false)
    private Integer productVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ValidityType validityType;

    private Integer validityAmount;

    @Enumerated(EnumType.STRING)
    private ValidityUnit validityUnit;

    @Column(nullable = false)
    private boolean renewable;

    @Column(nullable = false)
    private boolean stackable;

    @Column(nullable = false)
    private boolean requiresPrimaryTariff;

    @PrePersist
    @PreUpdate
    public void validateConfiguration() {
        if (productType == null) throw new IllegalStateException("productType is required");
        if (productVersion == null || productVersion <= 0) {
            throw new IllegalStateException("productVersion must be positive");
        }
        if (validityType == null) throw new IllegalStateException("validityType is required");

        if (validityType == ValidityType.FIXED_DURATION) {
            if (validityAmount == null || validityAmount <= 0 || validityUnit == null) {
                throw new IllegalStateException(
                        "FIXED_DURATION requires a positive validityAmount and validityUnit");
            }
        } else if (validityAmount != null || validityUnit != null) {
            throw new IllegalStateException("NON_EXPIRING must not define validityAmount or validityUnit");
        }
    }
}

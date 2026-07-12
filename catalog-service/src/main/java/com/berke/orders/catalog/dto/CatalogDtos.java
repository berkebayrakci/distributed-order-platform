package com.berke.orders.catalog.dto;

import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public class CatalogDtos {
    public record ProductLookupRequest(@NotEmpty List<@NotBlank String> sourceProductCodes) {
    }

    public record ProductMapItem(
            String sourceProductCode,
            String targetProductCode,
            String productType,
            Integer productVersion,
            String validityType,
            Integer validityAmount,
            String validityUnit,
            boolean renewable,
            boolean stackable,
            boolean requiresPrimaryTariff
    ) {
    }

    public record ProductLookupResponse(List<ProductMapItem> products) {
    }

    public record RuntimeMappingInsertItem(
            String sourceProductCode,
            String targetProductCode,
            String productType,
            String sourceItemRef,
            String targetItemRef
    ) {
    }

    public record RuntimeMappingInsertRequest(Long operationId, List<RuntimeMappingInsertItem> items) {
    }

    public record RuntimeMappingInsertResponse(Long universalProductKey) {
    }
}

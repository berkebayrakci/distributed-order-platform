package com.berke.orders.catalog.dto;

import java.util.List;

public class CatalogDtos {
    public record ProductLookupRequest(List<String> sourceProductCodes) {
    }

    public record ProductMapItem(String sourceProductCode, String targetProductCode) {
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

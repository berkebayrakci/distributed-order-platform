package com.berke.orders.catalog.web;

import com.berke.orders.catalog.dto.CatalogDtos.*;
import com.berke.orders.catalog.model.OrderProductInstanceMapping;
import com.berke.orders.catalog.repo.CatalogSequenceRepository;
import com.berke.orders.catalog.repo.InstanceMappingRepository;
import com.berke.orders.catalog.repo.ProductCodeMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Valid;

import java.util.HashSet;

@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogController {
    private final ProductCodeMappingRepository catalogRepo;
    private final InstanceMappingRepository instanceRepo;

    @PostMapping("/lookup")
    public ProductLookupResponse lookup(@Valid @RequestBody ProductLookupRequest req) {
        var uniqueCodes = new HashSet<>(req.sourceProductCodes());
        var found = catalogRepo.findBySourceProductCodeIn(req.sourceProductCodes());

        if (found.size() != uniqueCodes.size()) {
            throw new IllegalArgumentException("Catalog translation missing for one or more source product codes");
        }

        return new ProductLookupResponse(
                found.stream()
                        .map(p -> {
                            p.validateConfiguration();
                            return new ProductMapItem(
                                    p.getSourceProductCode(),
                                    p.getTargetProductCode(),
                                    p.getProductType().name(),
                                    p.getProductVersion(),
                                    p.getValidityType().name(),
                                    p.getValidityAmount(),
                                    p.getValidityUnit() == null ? null : p.getValidityUnit().name(),
                                    p.isRenewable(),
                                    p.isStackable(),
                                    p.isRequiresPrimaryTariff()
                            );
                        })
                        .toList()
        );
    }

    @PostMapping("/runtime-mappings")
    @Transactional
    public RuntimeMappingInsertResponse insertRuntimeMapping(@RequestBody RuntimeMappingInsertRequest req) {
        if (req.operationId() == null || req.items() == null || req.items().isEmpty()) {
            throw new IllegalArgumentException("operationId and at least one mapping item are required");
        }
        Long universalProductKey = req.operationId();
        var existing = instanceRepo.findByUniversalProductKeyOrderBySourceItemRef(universalProductKey);
        if (!existing.isEmpty()) {
            if (existing.size() != req.items().size()) {
                throw new IllegalStateException("Operation already has a different runtime mapping payload");
            }
            return new RuntimeMappingInsertResponse(universalProductKey);
        }

        for (var item : req.items()) {
            instanceRepo.save(OrderProductInstanceMapping.builder()
                    .universalProductKey(universalProductKey)
                    .sourceProductCode(item.sourceProductCode())
                    .targetProductCode(item.targetProductCode())
                    .productType(item.productType())
                    .sourceItemRef(item.sourceItemRef())
                    .targetItemRef(item.targetItemRef())
                    .build());
        }

        return new RuntimeMappingInsertResponse(universalProductKey);
    }

    @GetMapping("/runtime-mappings/{universalProductKey}")
    public Object byUniversalProductKey(@PathVariable Long universalProductKey) {
        return instanceRepo.findByUniversalProductKeyOrderBySourceItemRef(universalProductKey);
    }
}

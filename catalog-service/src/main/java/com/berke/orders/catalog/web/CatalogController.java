package com.berke.orders.catalog.web;

import com.berke.orders.catalog.dto.CatalogDtos.*;
import com.berke.orders.catalog.model.OrderProductInstanceMapping;
import com.berke.orders.catalog.repo.CatalogSequenceRepository;
import com.berke.orders.catalog.repo.InstanceMappingRepository;
import com.berke.orders.catalog.repo.ProductCodeMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;

@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogController {
    private final ProductCodeMappingRepository catalogRepo;
    private final InstanceMappingRepository instanceRepo;
    private final CatalogSequenceRepository sequenceRepo;

    @PostMapping("/lookup")
    public ProductLookupResponse lookup(@RequestBody ProductLookupRequest req) {
        var uniqueCodes = new HashSet<>(req.sourceProductCodes());
        var found = catalogRepo.findBySourceProductCodeIn(req.sourceProductCodes());

        if (found.size() != uniqueCodes.size()) {
            throw new IllegalArgumentException("Catalog translation missing for one or more source product codes");
        }

        return new ProductLookupResponse(
                found.stream()
                        .map(p -> new ProductMapItem(p.getSourceProductCode(), p.getTargetProductCode()))
                        .toList()
        );
    }

    @PostMapping("/runtime-mappings")
    public RuntimeMappingInsertResponse insertRuntimeMapping(@RequestBody RuntimeMappingInsertRequest req) {
        Long universalProductKey = sequenceRepo.nextUniversalProductKey();

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

package com.berke.orders.catalog.web;

import com.berke.orders.catalog.dto.CatalogDtos.RuntimeMappingInsertItem;
import com.berke.orders.catalog.dto.CatalogDtos.RuntimeMappingInsertRequest;
import com.berke.orders.catalog.model.OrderProductInstanceMapping;
import com.berke.orders.catalog.repo.InstanceMappingRepository;
import com.berke.orders.catalog.repo.ProductCodeMappingRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CatalogControllerTest {
    private final ProductCodeMappingRepository catalogRepo = mock(ProductCodeMappingRepository.class);
    private final InstanceMappingRepository instanceRepo = mock(InstanceMappingRepository.class);
    private final CatalogController controller = new CatalogController(catalogRepo, instanceRepo);

    @Test
    void runtimeMappingUsesOperationIdAndWritesEveryItemAtomically() {
        when(instanceRepo.findByUniversalProductKeyOrderBySourceItemRef(42L)).thenReturn(List.of());
        var request = new RuntimeMappingInsertRequest(42L, List.of(
                new RuntimeMappingInsertItem("A", "TA", "TARIFF", "source-1", "target-1"),
                new RuntimeMappingInsertItem("B", "TB", "ADDON", "source-2", "target-2")
        ));

        var response = controller.insertRuntimeMapping(request);

        assertEquals(42L, response.universalProductKey());
        verify(instanceRepo, times(2)).save(any(OrderProductInstanceMapping.class));
    }

    @Test
    void duplicateRuntimeMappingReturnsExistingIdentityWithoutWritingAgain() {
        var existing = OrderProductInstanceMapping.builder()
                .universalProductKey(42L).sourceProductCode("A").targetProductCode("TA")
                .productType("TARIFF").sourceItemRef("source-1").targetItemRef("target-1").build();
        when(instanceRepo.findByUniversalProductKeyOrderBySourceItemRef(42L)).thenReturn(List.of(existing));
        var request = new RuntimeMappingInsertRequest(42L, List.of(
                new RuntimeMappingInsertItem("A", "TA", "TARIFF", "source-1", "target-1")
        ));

        assertEquals(42L, controller.insertRuntimeMapping(request).universalProductKey());
        verify(instanceRepo, never()).save(any());
    }

    @Test
    void runtimeMappingRejectsMissingIdempotencyKey() {
        var request = new RuntimeMappingInsertRequest(null, List.of(
                new RuntimeMappingInsertItem("A", "TA", "TARIFF", "source-1", "target-1")
        ));

        assertThrows(IllegalArgumentException.class, () -> controller.insertRuntimeMapping(request));
    }
}

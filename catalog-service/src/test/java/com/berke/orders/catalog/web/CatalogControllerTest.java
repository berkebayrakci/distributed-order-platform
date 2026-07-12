package com.berke.orders.catalog.web;

import com.berke.orders.catalog.dto.CatalogDtos.RuntimeMappingInsertItem;
import com.berke.orders.catalog.dto.CatalogDtos.RuntimeMappingInsertRequest;
import com.berke.orders.catalog.model.OrderProductInstanceMapping;
import com.berke.orders.catalog.model.ProductCodeMapping;
import com.berke.orders.catalog.model.ProductType;
import com.berke.orders.catalog.model.ValidityType;
import com.berke.orders.catalog.model.ValidityUnit;
import com.berke.orders.catalog.dto.CatalogDtos.ProductLookupRequest;
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

    @Test
    void lookupReturnsSeedShapedTariffAndAddonValidityConfiguration() {
        var tariff = ProductCodeMapping.builder()
                .sourceProductCode("1893").targetProductCode("100074239")
                .productType(ProductType.TARIFF).productVersion(1)
                .validityType(ValidityType.FIXED_DURATION).validityAmount(1).validityUnit(ValidityUnit.YEARS)
                .renewable(false).stackable(false).requiresPrimaryTariff(false).build();
        var addon = ProductCodeMapping.builder()
                .sourceProductCode("41001").targetProductCode("90041001")
                .productType(ProductType.ADDON).productVersion(1)
                .validityType(ValidityType.FIXED_DURATION).validityAmount(3).validityUnit(ValidityUnit.MONTHS)
                .renewable(false).stackable(true).requiresPrimaryTariff(true).build();
        when(catalogRepo.findBySourceProductCodeIn(List.of("1893", "41001")))
                .thenReturn(List.of(tariff, addon));

        var response = controller.lookup(new ProductLookupRequest(List.of("1893", "41001")));

        var tariffResponse = response.products().stream()
                .filter(p -> p.sourceProductCode().equals("1893")).findFirst().orElseThrow();
        assertEquals("TARIFF", tariffResponse.productType());
        assertEquals("FIXED_DURATION", tariffResponse.validityType());
        assertEquals(1, tariffResponse.validityAmount());
        assertEquals("YEARS", tariffResponse.validityUnit());

        var addonResponse = response.products().stream()
                .filter(p -> p.sourceProductCode().equals("41001")).findFirst().orElseThrow();
        assertEquals("ADDON", addonResponse.productType());
        assertEquals(3, addonResponse.validityAmount());
        assertEquals("MONTHS", addonResponse.validityUnit());
        assertTrue(addonResponse.stackable());
        assertTrue(addonResponse.requiresPrimaryTariff());
    }

    @Test
    void fixedDurationRejectsMissingOrNonPositiveDuration() {
        var invalid = ProductCodeMapping.builder()
                .sourceProductCode("BAD").targetProductCode("BAD-TARGET")
                .productType(ProductType.ADDON).productVersion(1)
                .validityType(ValidityType.FIXED_DURATION).validityAmount(0).validityUnit(ValidityUnit.MONTHS)
                .build();

        assertThrows(IllegalStateException.class, invalid::validateConfiguration);
    }

    @Test
    void nonExpiringRejectsDurationFields() {
        var invalid = ProductCodeMapping.builder()
                .sourceProductCode("BAD").targetProductCode("BAD-TARGET")
                .productType(ProductType.ADDON).productVersion(1)
                .validityType(ValidityType.NON_EXPIRING).validityAmount(3).validityUnit(ValidityUnit.MONTHS)
                .build();

        assertThrows(IllegalStateException.class, invalid::validateConfiguration);
    }
}

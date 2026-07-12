package com.berke.orders.orchestrator.routes;

import com.berke.orders.orchestrator.config.IntegrationProperties;
import com.berke.orders.orchestrator.dto.OrchestratorDtos.ProductResult;
import com.berke.orders.orchestrator.dto.OrchestratorDtos.ProductResultItem;
import com.berke.orders.orchestrator.exception.ProtocolFailureException;
import com.berke.orders.orchestrator.repo.CustomerRequestRepository;
import com.berke.orders.orchestrator.repo.ProductOrderRepository;
import com.berke.orders.orchestrator.service.CallbackClient;
import com.berke.orders.orchestrator.service.ProductOrderFinalizationService;
import com.berke.orders.orchestrator.service.TraceLogService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class OrchestrationResultValidationTest {

    @Test
    void malformedResultWithoutOrderIdIsProtocolFailure() {
        var routes = routes();
        var result = new ProductResult(null, "customer-1", true, null, List.of());

        assertThrows(ProtocolFailureException.class,
                () -> ReflectionTestUtils.invokeMethod(routes, "validateProductResult", result));
    }

    @Test
    void unsupportedProductTypeIsProtocolFailure() {
        var routes = routes();
        var item = new ProductResultItem(
                "source", "target", "source-ref", "target-ref", "UNSUPPORTED");
        var result = new ProductResult(42L, "customer-1", true, null, List.of(item));

        assertThrows(ProtocolFailureException.class,
                () -> ReflectionTestUtils.invokeMethod(routes, "validateProductResult", result));
    }

    @Test
    void unsupportedEnvelopeVersionIsProtocolFailure() {
        var routes = routes();

        assertThrows(ProtocolFailureException.class, () -> ReflectionTestUtils.invokeMethod(routes,
                "validateEnvelope", UUID.randomUUID(), "ProductResult", 2, UUID.randomUUID(),
                UUID.randomUUID(), "subscriber-service", Instant.now(), new Object(), "ProductResult"));
    }

    private OrchestrationRoutes routes() {
        return new OrchestrationRoutes(
                mock(TraceLogService.class),
                mock(ProductOrderRepository.class),
                mock(CustomerRequestRepository.class),
                mock(IntegrationProperties.class),
                mock(CallbackClient.class),
                mock(ProductOrderFinalizationService.class)
        );
    }
}

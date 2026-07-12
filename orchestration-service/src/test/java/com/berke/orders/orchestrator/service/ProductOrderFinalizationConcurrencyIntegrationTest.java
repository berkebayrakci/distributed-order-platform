package com.berke.orders.orchestrator.service;

import com.berke.orders.orchestrator.config.CallbackOutboxProperties;
import com.berke.orders.orchestrator.model.CallbackOutboxStatus;
import com.berke.orders.orchestrator.model.OrderStatus;
import com.berke.orders.orchestrator.model.ProductOrder;
import com.berke.orders.orchestrator.repo.CallbackOutboxRepository;
import com.berke.orders.orchestrator.repo.ProductOrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:order-finalization;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"
})
@Import({
        ProductOrderFinalizationService.class,
        CallbackOutboxProperties.class,
        ProductOrderFinalizationConcurrencyIntegrationTest.TestConfig.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProductOrderFinalizationConcurrencyIntegrationTest {
    @Autowired
    private ProductOrderFinalizationService finalizationService;

    @Autowired
    private ProductOrderRepository orderRepository;

    @Autowired
    private CallbackOutboxRepository outboxRepository;

    @Test
    void concurrentDuplicateResultsProduceOneClaimAndOneOutboxRecord() throws Exception {
        orderRepository.saveAndFlush(ProductOrder.builder()
                .orderId(81L)
                .customerId("customer-1")
                .crmCallbackUrl("http://crm-service/api/orders/callback")
                .status(OrderStatus.IN_PROGRESS)
                .build());
        var start = new CountDownLatch(1);

        boolean firstClaim;
        boolean secondClaim;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return finalizationService.claim(81L);
            });
            var second = executor.submit(() -> {
                start.await();
                return finalizationService.claim(81L);
            });
            start.countDown();
            firstClaim = first.get(10, TimeUnit.SECONDS);
            secondClaim = second.get(10, TimeUnit.SECONDS);
        }

        assertTrue(firstClaim ^ secondClaim, "Exactly one consumer must claim finalization");
        assertEquals(OrderStatus.FINALIZING, orderRepository.findById(81L).orElseThrow().getStatus());

        assertTrue(finalizationService.complete(81L, 9001L));
        assertFalse(finalizationService.complete(81L, 9001L));

        var completed = orderRepository.findById(81L).orElseThrow();
        assertEquals(OrderStatus.COMPLETED, completed.getStatus());
        assertEquals(9001L, completed.getUniversalProductKey());
        assertEquals(1, outboxRepository.count());
        assertEquals(CallbackOutboxStatus.PENDING, outboxRepository.findAll().getFirst().getStatus());
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}

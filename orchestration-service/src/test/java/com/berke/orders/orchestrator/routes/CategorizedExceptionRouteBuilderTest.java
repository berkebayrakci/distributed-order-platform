package com.berke.orders.orchestrator.routes;

import com.berke.orders.orchestrator.exception.BusinessFailureException;
import com.berke.orders.orchestrator.exception.ProtocolFailureException;
import com.berke.orders.orchestrator.exception.TransientInfrastructureException;
import com.berke.orders.orchestrator.dto.OrchestratorDtos.ProductResult;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CategorizedExceptionRouteBuilderTest {
    private DefaultCamelContext context;
    private ProducerTemplate producer;
    private TestRoutes routes;

    @BeforeEach
    void startCamel() throws Exception {
        context = new DefaultCamelContext();
        routes = new TestRoutes();
        context.addRoutes(routes);
        context.start();
        producer = context.createProducerTemplate();
    }

    @AfterEach
    void stopCamel() {
        if (producer != null) producer.stop();
        if (context != null) context.stop();
    }

    @Test
    void businessFailureIsDeterministicAndNotRetried() {
        producer.requestBody("direct:business", "request");

        assertEquals(1, routes.businessAttempts.get());
        assertEquals(1, routes.terminalFailures.get());
    }

    @Test
    void protocolFailureFromResultRouteIsSentToDlqWithoutRetry() {
        producer.requestBody("direct:protocol", "malformed-result");

        assertEquals(1, routes.protocolAttempts.get());
        assertEquals(1, routes.deadLetters.get());
        assertEquals(0, routes.terminalFailures.get());
    }

    @Test
    void malformedJsonIsSentToDlq() {
        producer.requestBody("direct:malformed-json", "{not-json");

        assertEquals(1, routes.deadLetters.get());
        assertEquals(0, routes.terminalFailures.get());
    }

    @Test
    void directValidationFailureFailsOperationWithoutRetry() {
        producer.requestBody("direct:validation", "invalid-request");

        assertEquals(1, routes.validationAttempts.get());
        assertEquals(1, routes.terminalFailures.get());
        assertEquals(0, routes.deadLetters.get());
    }

    @Test
    void transientInfrastructureFailureUsesBoundedRedeliveryAndRemainsFailed() {
        assertThrows(CamelExecutionException.class,
                () -> producer.requestBody("direct:transient", "request"));

        assertEquals(1 + CategorizedExceptionRouteBuilder.TRANSIENT_MAX_REDELIVERIES,
                routes.transientAttempts.get());
        assertEquals(0, routes.terminalFailures.get());
    }

    @Test
    void unexpectedProgrammingDefectIsNotHandledOrRetried() {
        assertThrows(CamelExecutionException.class,
                () -> producer.requestBody("direct:unexpected", "request"));

        assertEquals(1, routes.unexpectedAttempts.get());
        assertEquals(0, routes.terminalFailures.get());
    }

    private static final class TestRoutes extends CategorizedExceptionRouteBuilder {
        private final AtomicInteger businessAttempts = new AtomicInteger();
        private final AtomicInteger protocolAttempts = new AtomicInteger();
        private final AtomicInteger transientAttempts = new AtomicInteger();
        private final AtomicInteger validationAttempts = new AtomicInteger();
        private final AtomicInteger unexpectedAttempts = new AtomicInteger();
        private final AtomicInteger terminalFailures = new AtomicInteger();
        private final AtomicInteger deadLetters = new AtomicInteger();

        @Override
        public void configure() {
            configureExceptionPolicies(
                    exchange -> terminalFailures.incrementAndGet(),
                    "direct:protocol-dlq-test"
            );

            from("direct:protocol-dlq-test")
                    .process(exchange -> deadLetters.incrementAndGet());

            from("direct:business")
                    .process(exchange -> {
                        businessAttempts.incrementAndGet();
                        throw new BusinessFailureException("deterministic rejection");
                    });

            from("direct:protocol")
                    .setProperty(RESULT_DLQ_PROPERTY, constant("test-result.dlq"))
                    .process(exchange -> {
                        protocolAttempts.incrementAndGet();
                        throw new ProtocolFailureException("malformed result");
                    });

            from("direct:malformed-json")
                    .setProperty(RESULT_DLQ_PROPERTY, constant("test-result.dlq"))
                    .unmarshal()
                    .json(ProductResult.class);

            from("direct:validation")
                    .process(exchange -> {
                        validationAttempts.incrementAndGet();
                        throw new ProtocolFailureException("invalid direct request");
                    });

            from("direct:transient")
                    .process(exchange -> {
                        transientAttempts.incrementAndGet();
                        throw new TransientInfrastructureException(
                                "database unavailable", new IllegalStateException("offline"));
                    });

            from("direct:unexpected")
                    .process(exchange -> {
                        unexpectedAttempts.incrementAndGet();
                        throw new NullPointerException("programming defect");
                    });
        }
    }
}

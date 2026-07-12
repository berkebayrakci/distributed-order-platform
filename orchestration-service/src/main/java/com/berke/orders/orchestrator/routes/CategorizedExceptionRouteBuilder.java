package com.berke.orders.orchestrator.routes;

import com.berke.orders.orchestrator.exception.BusinessFailureException;
import com.berke.orders.orchestrator.exception.ProtocolFailureException;
import com.berke.orders.orchestrator.exception.TransientInfrastructureException;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpIOException;
import org.springframework.amqp.AmqpTimeoutException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionTimedOutException;

public abstract class CategorizedExceptionRouteBuilder extends RouteBuilder {
    public static final String RESULT_DLQ_PROPERTY = "resultDlq";
    public static final int TRANSIENT_MAX_REDELIVERIES = 3;
    public static final long TRANSIENT_INITIAL_DELAY_MS = 250L;
    public static final double TRANSIENT_BACKOFF_MULTIPLIER = 2.0;
    public static final long TRANSIENT_MAX_DELAY_MS = 2_000L;

    protected void configureExceptionPolicies(
            Processor terminalFailureProcessor,
            String protocolDeadLetterEndpoint
    ) {
        onException(BusinessFailureException.class)
                .maximumRedeliveries(0)
                .handled(true)
                .process(terminalFailureProcessor);

        onException(ProtocolFailureException.class, JsonProcessingException.class, InvalidPayloadException.class)
                .onWhen(exchangeProperty(RESULT_DLQ_PROPERTY).isNotNull())
                .maximumRedeliveries(0)
                .useOriginalMessage()
                .handled(true)
                .to(protocolDeadLetterEndpoint);

        onException(ProtocolFailureException.class, JsonProcessingException.class, InvalidPayloadException.class)
                .maximumRedeliveries(0)
                .handled(true)
                .process(terminalFailureProcessor);

        onException(
                TransientInfrastructureException.class,
                TransientDataAccessException.class,
                RecoverableDataAccessException.class,
                CannotCreateTransactionException.class,
                TransactionTimedOutException.class,
                AmqpConnectException.class,
                AmqpIOException.class,
                AmqpTimeoutException.class
        )
                .maximumRedeliveries(TRANSIENT_MAX_REDELIVERIES)
                .redeliveryDelay(TRANSIENT_INITIAL_DELAY_MS)
                .useExponentialBackOff()
                .backOffMultiplier(TRANSIENT_BACKOFF_MULTIPLIER)
                .maximumRedeliveryDelay(TRANSIENT_MAX_DELAY_MS)
                .retryAttemptedLogLevel(LoggingLevel.WARN)
                .handled(false);
    }
}

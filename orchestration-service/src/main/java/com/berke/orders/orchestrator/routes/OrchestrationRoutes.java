package com.berke.orders.orchestrator.routes;

import com.berke.orders.orchestrator.dto.OrchestratorDtos.*;
import com.berke.orders.orchestrator.config.IntegrationProperties;
import com.berke.orders.orchestrator.exception.BusinessFailureException;
import com.berke.orders.orchestrator.exception.ProtocolFailureException;
import com.berke.orders.orchestrator.exception.TransientInfrastructureException;
import com.berke.orders.orchestrator.repo.CustomerRequestRepository;
import com.berke.orders.orchestrator.repo.ProductOrderRepository;
import com.berke.orders.orchestrator.service.OrchestratorService;
import com.berke.orders.orchestrator.service.TraceLogService;
import com.berke.orders.orchestrator.service.CallbackClient;
import com.berke.orders.orchestrator.service.ProductOrderFinalizationService;
import lombok.RequiredArgsConstructor;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class OrchestrationRoutes extends CategorizedExceptionRouteBuilder {
    private static final String PROCESS_CLAIMED_RESULT = "processClaimedResult";
    private static final String PROTOCOL_DLQ_ENDPOINT = "direct:result-protocol-dead-letter";
    private static final String PRODUCT_RESULT_DLQ = "subscriber.product.result.dlq";
    private static final String CUSTOMER_RESULT_DLQ = "subscriber.customer.result.dlq";
    private static final Set<String> SUPPORTED_PRODUCT_TYPES = Set.of("TARIFF", "CAMPAIGN", "ADDON");

    private final TraceLogService log;
    private final ProductOrderRepository orderRepo;
    private final CustomerRequestRepository customerRepo;
    private final IntegrationProperties integrations;
    private final CallbackClient callbackClient;
    private final ProductOrderFinalizationService productOrderFinalizationService;
    private final RestClient rest = RestClient.builder().build();

    @Override
    public void configure() {
        configureExceptionPolicies(this::handleTerminalFailure, PROTOCOL_DLQ_ENDPOINT);

        from(PROTOCOL_DLQ_ENDPOINT)
                .routeId("result-protocol-dead-letter-route")
                .choice()
                    .when(exchangeProperty(RESULT_DLQ_PROPERTY).isEqualTo(PRODUCT_RESULT_DLQ))
                        .to("spring-rabbitmq:?routingKey=subscriber.product.result.dlq&autoDeclare=false")
                    .when(exchangeProperty(RESULT_DLQ_PROPERTY).isEqualTo(CUSTOMER_RESULT_DLQ))
                        .to("spring-rabbitmq:?routingKey=subscriber.customer.result.dlq&autoDeclare=false")
                    .otherwise()
                        .process(exchange -> {
                            throw new IllegalStateException("Missing or unsupported result DLQ destination");
                        })
                .end();

        from("direct:processProductOrder")
            .routeId("crm-to-subscriber-product-route")
            .process(exchange -> {
                    var env = exchange.getIn().getBody(OrchestratorService.ProductOrderEnvelope.class);
                    if (env == null || env.request() == null) {
                        throw new ProtocolFailureException("Product order envelope or request is missing");
                    }
                    var req = env.request();
                    Long orderId = env.orderId();
                    exchange.getIn().setHeader("operationId", orderId);
                    exchange.getIn().setHeader("operationType", "PRODUCT_ORDER");

                    log.log(orderId, "Request from CRM", "START", "SUCCESS", req, null, null);

                    log.log(orderId, "Request to Catalog", "START", "SUCCESS", req.products(), null, null);
                    var lookupReq = new ProductLookupRequest(
                            req.products().stream().map(ProductRequest::sourceProductCode).toList()
                    );
                    var catalogResponse = callCatalog(() -> rest.post()
                                    .uri(integrations.getCatalogBaseUrl() + "/api/catalog/lookup")
                                    .header("X-Internal-Api-Key", integrations.getInternalApiKey())
                                    .body(lookupReq)
                                    .retrieve()
                                    .body(ProductLookupResponse.class),
                            "product lookup");
                    validateCatalogResponse(catalogResponse);
                    log.log(orderId, "Response from Catalog", "END", "SUCCESS", lookupReq, catalogResponse, null);

                    Map<String, ProductMapItem> productMap = new HashMap<>();
                    catalogResponse.products().forEach(p -> {
                        if (productMap.put(p.sourceProductCode(), p) != null) {
                            throw new ProtocolFailureException(
                                    "Catalog returned duplicate mappings for source product code: " + p.sourceProductCode());
                        }
                    });

                    var items = req.products().stream().map(product -> {
                        var mapped = productMap.get(product.sourceProductCode());
                        if (mapped == null) {
                            throw new BusinessFailureException(
                                    "Catalog translation missing for source product code: " + product.sourceProductCode());
                        }
                        return new ProductCommandItem(
                                product.sourceProductCode(),
                                mapped.targetProductCode(),
                                product.sourceItemRef(),
                                product.productType()
                        );
                    }).toList();

                    var command = new ProductCommand(orderId, req.customerId(), items);
                    log.log(orderId, "Request to Subscriber", "START", "SUCCESS", command, null, null);
                    exchange.getIn().setBody(command);
                })
            .marshal()
            .json()
            .setHeader("contentType", constant("application/json"))
            .to("spring-rabbitmq:?routingKey=subscriber.product.command.queue&autoDeclare=false");

        from("spring-rabbitmq:?queues=subscriber.product.result.queue&autoDeclare=false"
                + "&bridgeErrorHandler=true&maximumRetryAttempts=1&rejectAndDontRequeue=true")
            .routeId("subscriber-product-result-route")
            .setProperty(RESULT_DLQ_PROPERTY, constant(PRODUCT_RESULT_DLQ))
            .unmarshal()
            .json(ProductResult.class)
            .process(exchange -> {
                    var result = exchange.getIn().getBody(ProductResult.class);
                    validateProductResult(result);
                    Long orderId = result.orderId();
                    exchange.getIn().setHeader("operationId", orderId);
                    exchange.getIn().setHeader("operationType", "PRODUCT_ORDER");

                    if (!productOrderFinalizationService.claim(orderId)) {
                        if (!orderRepo.existsById(orderId)) {
                            throw new ProtocolFailureException("Product order not found: " + orderId);
                        }
                        exchange.setProperty(PROCESS_CLAIMED_RESULT, false);
                        return;
                    }
                    exchange.setProperty(PROCESS_CLAIMED_RESULT, true);
                })
            .filter(exchangeProperty(PROCESS_CLAIMED_RESULT).isEqualTo(true))
            .process(exchange -> {
                    var result = exchange.getIn().getBody(ProductResult.class);
                    Long orderId = result.orderId();
                    log.log(orderId, "Response from Subscriber", "END", result.success() ? "SUCCESS" : "FAILED", null, result, result.errorMessage());
                    if (!result.success()) {
                        failOrder(orderId, result.errorMessage());
                        return;
                    }

                    log.log(orderId, "Request to Catalog", "START", "SUCCESS", result, null, null);
                    var insertRequest = new RuntimeMappingInsertRequest(orderId,
                            result.items().stream()
                                    .map(i -> new RuntimeMappingInsertItem(
                                            i.sourceProductCode(),
                                            i.targetProductCode(),
                                            i.productType(),
                                            i.sourceItemRef(),
                                            i.targetItemRef()
                                    ))
                                    .toList()
                    );
                    var insertResponse = callCatalog(() -> rest.post()
                                    .uri(integrations.getCatalogBaseUrl() + "/api/catalog/runtime-mappings")
                                    .header("X-Internal-Api-Key", integrations.getInternalApiKey())
                                    .body(insertRequest)
                                    .retrieve()
                                    .body(RuntimeMappingInsertResponse.class),
                            "runtime mapping insertion");
                    if (insertResponse.universalProductKey() == null) {
                        throw new ProtocolFailureException("Catalog runtime mapping response has no universal product key");
                    }
                    log.log(orderId, "Response from Catalog", "END", "SUCCESS", insertRequest, insertResponse, null);

                    completeOrder(orderId, insertResponse.universalProductKey());
                })
            .end();

        from("direct:processCustomerRequest")
            .routeId("crm-to-subscriber-customer-route")
            .process(exchange -> {
                    var env = exchange.getIn().getBody(OrchestratorService.CustomerEnvelope.class);
                    if (env == null || env.request() == null) {
                        throw new ProtocolFailureException("Customer request envelope or request is missing");
                    }
                    var req = env.request();
                    Long requestId = env.requestId();
                    exchange.getIn().setHeader("operationId", requestId);
                    exchange.getIn().setHeader("operationType", "CUSTOMER_CREATE");

                    log.log(requestId, "Request from CRM", "START", "SUCCESS", req, null, null);
                    var command = new CustomerCommand(requestId, req.customerId(), req.firstName(), req.lastName());
                    log.log(requestId, "Request to Subscriber", "START", "SUCCESS", command, null, null);
                    exchange.getIn().setBody(command);
                })
            .marshal()
            .json()
            .setHeader("contentType", constant("application/json"))
            .to("spring-rabbitmq:?routingKey=subscriber.customer.command.queue&autoDeclare=false");

        from("spring-rabbitmq:?queues=subscriber.customer.result.queue&autoDeclare=false"
                + "&bridgeErrorHandler=true&maximumRetryAttempts=1&rejectAndDontRequeue=true")
            .routeId("subscriber-customer-result-route")
            .setProperty(RESULT_DLQ_PROPERTY, constant(CUSTOMER_RESULT_DLQ))
            .unmarshal()
            .json(CustomerResult.class)
            .process(exchange -> {
                    var result = exchange.getIn().getBody(CustomerResult.class);
                    validateCustomerResult(result);
                    Long requestId = result.requestId();
                    exchange.getIn().setHeader("operationId", requestId);
                    exchange.getIn().setHeader("operationType", "CUSTOMER_CREATE");

                    var current = customerRepo.findById(requestId).orElseThrow();
                    if (!"IN_PROGRESS".equals(current.getStatus())) return;

                    log.log(requestId, "Response from Subscriber", "END", result.success() ? "SUCCESS" : "FAILED", null, result, result.errorMessage());
                    if (!result.success()) {
                        failCustomer(requestId, result.errorMessage());
                        return;
                    }
                    completeCustomer(requestId);
                });
    }

    private void completeOrder(Long orderId, Long universalProductKey) {
        if (productOrderFinalizationService.complete(orderId, universalProductKey)) {
            log.log(orderId, "CRM callback queued", "END", "SUCCESS", null,
                    new ProductOrderCallback(orderId, "COMPLETED", null), null);
        }
    }

    private void handleTerminalFailure(org.apache.camel.Exchange exchange) {
        Exception exception = exchange.getProperty(org.apache.camel.Exchange.EXCEPTION_CAUGHT, Exception.class);
        String message = exception == null || exception.getMessage() == null
                ? "Unspecified orchestration failure"
                : exception.getMessage();
        Long operationId = exchange.getIn().getHeader("operationId", Long.class);
        String operationType = exchange.getIn().getHeader("operationType", String.class);

        if (operationId == null || operationType == null) {
            throw new IllegalStateException("Categorized failure has no operation identity", exception);
        }
        if ("PRODUCT_ORDER".equals(operationType)) {
            failOrder(operationId, message);
            return;
        }
        if ("CUSTOMER_CREATE".equals(operationType)) {
            failCustomer(operationId, message);
            return;
        }
        throw new IllegalStateException("Unsupported operation type in failure handler: " + operationType, exception);
    }

    private <T> T callCatalog(Supplier<T> call, String action) {
        try {
            T response = call.get();
            if (response == null) {
                throw new ProtocolFailureException("Catalog returned an empty response for " + action);
            }
            return response;
        } catch (HttpClientErrorException e) {
            throw new ProtocolFailureException(
                    "Catalog rejected " + action + " with HTTP " + e.getStatusCode().value(), e);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw new TransientInfrastructureException("Catalog unavailable during " + action, e);
        }
    }

    private void validateCatalogResponse(ProductLookupResponse response) {
        if (response.products() == null) {
            throw new ProtocolFailureException("Catalog lookup response has no products collection");
        }
        for (var product : response.products()) {
            if (product == null
                    || isBlank(product.sourceProductCode())
                    || isBlank(product.targetProductCode())) {
                throw new ProtocolFailureException("Catalog lookup response contains an invalid mapping");
            }
        }
    }

    private void validateProductResult(ProductResult result) {
        if (result == null || result.orderId() == null) {
            throw new ProtocolFailureException("Subscriber product result has no order ID");
        }
        if (!result.success()) {
            if (isBlank(result.errorMessage())) {
                throw new ProtocolFailureException("Failed subscriber product result has no error message");
            }
            return;
        }
        if (result.items() == null || result.items().isEmpty()) {
            throw new ProtocolFailureException("Successful subscriber product result has no items");
        }

        var sourceReferences = new HashSet<String>();
        var targetReferences = new HashSet<String>();
        for (var item : result.items()) {
            if (item == null
                    || isBlank(item.sourceProductCode())
                    || isBlank(item.targetProductCode())
                    || isBlank(item.sourceItemRef())
                    || isBlank(item.targetItemRef())) {
                throw new ProtocolFailureException("Subscriber product result contains an incomplete item");
            }
            if (!SUPPORTED_PRODUCT_TYPES.contains(item.productType())) {
                throw new ProtocolFailureException(
                        "Unsupported product type in subscriber result: " + item.productType());
            }
            if (!sourceReferences.add(item.sourceItemRef()) || !targetReferences.add(item.targetItemRef())) {
                throw new ProtocolFailureException("Subscriber product result contains duplicate item references");
            }
        }
    }

    private void validateCustomerResult(CustomerResult result) {
        if (result == null || result.requestId() == null) {
            throw new ProtocolFailureException("Subscriber customer result has no request ID");
        }
        if (!result.success() && isBlank(result.errorMessage())) {
            throw new ProtocolFailureException("Failed subscriber customer result has no error message");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void failOrder(Long orderId, String error) {
        if (productOrderFinalizationService.fail(orderId, error)) {
            log.log(orderId, "CRM callback queued", "END", "FAILED", null,
                    new ProductOrderCallback(orderId, "FAILED", error), error);
        }
    }

    private void completeCustomer(Long requestId) {
        var request = customerRepo.findById(requestId).orElseThrow();
        if (!"IN_PROGRESS".equals(request.getStatus())) return;
        request.setStatus("COMPLETED");
        customerRepo.save(request);

        var callback = new CustomerCallback(requestId, "COMPLETED", null);
        callbackClient.post(request.getCrmCallbackUrl(), callback, UUID.randomUUID());
        log.log(requestId, "Response to CRM", "END", "SUCCESS", null, callback, null);
    }

    private void failCustomer(Long requestId, String error) {
        var request = customerRepo.findById(requestId).orElseThrow();
        if (!"IN_PROGRESS".equals(request.getStatus())) return;
        request.setStatus("FAILED");
        request.setErrorMessage(error);
        customerRepo.save(request);

        var callback = new CustomerCallback(requestId, "FAILED", error);
        callbackClient.post(request.getCrmCallbackUrl(), callback, UUID.randomUUID());
        log.log(requestId, "Response to CRM", "END", "FAILED", null, callback, error);
    }
}

package com.berke.orders.orchestrator.routes;

import com.berke.orders.orchestrator.dto.OrchestratorDtos.*;
import com.berke.orders.orchestrator.config.IntegrationProperties;
import com.berke.orders.orchestrator.exception.BusinessFailureException;
import com.berke.orders.orchestrator.exception.ProtocolFailureException;
import com.berke.orders.orchestrator.exception.TransientInfrastructureException;
import com.berke.orders.orchestrator.repo.CustomerRequestRepository;
import com.berke.orders.orchestrator.repo.ProductOrderRepository;
import com.berke.orders.orchestrator.model.ProductOrder;
import com.berke.orders.orchestrator.model.ProductOrderAction;
import com.berke.orders.orchestrator.service.OrchestratorService;
import com.berke.orders.orchestrator.service.TraceLogService;
import com.berke.orders.orchestrator.service.CallbackClient;
import com.berke.orders.orchestrator.service.ProductOrderFinalizationService;
import com.berke.orders.orchestrator.service.CustomerResultFinalizationService;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.time.Instant;

@Component
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
    private final CustomerResultFinalizationService customerResultFinalizationService;
    private final RestClient rest = RestClient.builder().build();

    @org.springframework.beans.factory.annotation.Autowired
    public OrchestrationRoutes(TraceLogService log, ProductOrderRepository orderRepo,
                               CustomerRequestRepository customerRepo, IntegrationProperties integrations,
                               CallbackClient callbackClient,
                               ProductOrderFinalizationService productOrderFinalizationService,
                               CustomerResultFinalizationService customerResultFinalizationService) {
        this.log = log;
        this.orderRepo = orderRepo;
        this.customerRepo = customerRepo;
        this.integrations = integrations;
        this.callbackClient = callbackClient;
        this.productOrderFinalizationService = productOrderFinalizationService;
        this.customerResultFinalizationService = customerResultFinalizationService;
    }

    public OrchestrationRoutes(TraceLogService log, ProductOrderRepository orderRepo,
                               CustomerRequestRepository customerRepo, IntegrationProperties integrations,
                               CallbackClient callbackClient,
                               ProductOrderFinalizationService productOrderFinalizationService) {
        this(log, orderRepo, customerRepo, integrations, callbackClient,
                productOrderFinalizationService, null);
    }

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
                    UUID correlationId = env.correlationId();
                    exchange.getIn().setHeader("operationId", orderId);
                    exchange.getIn().setHeader("operationType", "PRODUCT_ORDER");
                    exchange.getIn().setHeader("correlationId", correlationId);

                    log.log(orderId, correlationId, "Request from CRM", "START", "SUCCESS", req, null, null);

                    List<ProductRequest> requestedProducts = switch (req.action()) {
                        case ADD -> req.products();
                        case REMOVE -> List.of();
                        case CHANGE -> List.of(new ProductRequest(
                                req.newProductCode(), "CHANGE-" + orderId, "TARIFF"));
                    };
                    List<ProductCommandItem> items = requestedProducts.isEmpty()
                            ? List.of()
                            : resolveProducts(orderId, correlationId, requestedProducts);

                    var command = new ProductCommand(orderId, req.customerId(), req.action(),
                            req.productInstanceId(), req.existingProductInstanceId(), req.reason(), items);
                    int eventVersion = switch (req.action()) {
                        case ADD -> 1;
                        case REMOVE -> 2;
                        case CHANGE -> 3;
                    };
                    var event = new ProductCommandEvent(UUID.randomUUID(), "ProductCommand", eventVersion, correlationId,
                            env.causationId(), "orchestration-service", Instant.now(), command);
                    log.log(orderId, correlationId, "Request to Subscriber", "START", "SUCCESS", event, null, null);
                    exchange.getIn().setBody(event);
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
            .json(ProductResultEvent.class)
            .process(exchange -> {
                    var event = exchange.getIn().getBody(ProductResultEvent.class);
                    if (event == null) throw new ProtocolFailureException("ProductResult envelope is missing");
                    validateEnvelope(event.eventId(), event.eventType(), event.eventVersion(), event.correlationId(),
                            event.causationId(), event.producer(), event.occurredAt(), event.payload(), "ProductResult");
                    var result = event.payload();
                    validateProductResult(result, event.eventVersion());
                    Long orderId = result.orderId();
                    exchange.getIn().setHeader("operationId", orderId);
                    exchange.getIn().setHeader("operationType", "PRODUCT_ORDER");
                    exchange.getIn().setHeader("correlationId", event.correlationId());

                    var order = orderRepo.findById(orderId)
                            .orElseThrow(() -> new ProtocolFailureException("Product order not found: " + orderId));
                    validateProductResultMatchesOrder(result, event.eventVersion(), order);
                    if (!productOrderFinalizationService.claim(orderId)) {
                        exchange.setProperty(PROCESS_CLAIMED_RESULT, false);
                        return;
                    }
                    exchange.setProperty(PROCESS_CLAIMED_RESULT, true);
                })
            .filter(exchangeProperty(PROCESS_CLAIMED_RESULT).isEqualTo(true))
            .process(exchange -> {
                    var event = exchange.getIn().getBody(ProductResultEvent.class);
                    var result = event.payload();
                    Long orderId = result.orderId();
                    log.log(orderId, event.correlationId(), "Response from Subscriber", "END", result.success() ? "SUCCESS" : "FAILED", null, event, result.errorMessage());
                    if (!result.success()) {
                        failOrder(orderId, result.errorMessage(), event);
                        return;
                    }

                    if (productAction(result.action(), event.eventVersion()) == ProductOrderAction.REMOVE) {
                        completeOrder(orderId, null, event);
                        return;
                    }

                    log.log(orderId, event.correlationId(), "Request to Catalog", "START", "SUCCESS", result, null, null);
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
                    log.log(orderId, event.correlationId(), "Response from Catalog", "END", "SUCCESS", insertRequest, insertResponse, null);

                    completeOrder(orderId, insertResponse.universalProductKey(), event);
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
                    UUID correlationId = env.correlationId();
                    exchange.getIn().setHeader("operationId", requestId);
                    exchange.getIn().setHeader("operationType", "CUSTOMER_CREATE");
                    exchange.getIn().setHeader("correlationId", correlationId);

                    log.log(requestId, correlationId, "Request from CRM", "START", "SUCCESS", req, null, null);
                    var command = new CustomerCommand(requestId, req.customerId(), req.firstName(), req.lastName());
                    var event = new CustomerCommandEvent(UUID.randomUUID(), "CustomerCommand", 1, correlationId,
                            env.causationId(), "orchestration-service", Instant.now(), command);
                    log.log(requestId, correlationId, "Request to Subscriber", "START", "SUCCESS", event, null, null);
                    exchange.getIn().setBody(event);
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
            .json(CustomerResultEvent.class)
            .process(exchange -> {
                    var event = exchange.getIn().getBody(CustomerResultEvent.class);
                    if (event == null) throw new ProtocolFailureException("CustomerResult envelope is missing");
                    validateEnvelope(event.eventId(), event.eventType(), event.eventVersion(), event.correlationId(),
                            event.causationId(), event.producer(), event.occurredAt(), event.payload(), "CustomerResult");
                    var result = event.payload();
                    validateCustomerResult(result);
                    Long requestId = result.requestId();
                    exchange.getIn().setHeader("operationId", requestId);
                    exchange.getIn().setHeader("operationType", "CUSTOMER_CREATE");
                    exchange.getIn().setHeader("correlationId", event.correlationId());

                    log.log(requestId, event.correlationId(), "Response from Subscriber", "END", result.success() ? "SUCCESS" : "FAILED", null, event, result.errorMessage());
                    customerResultFinalizationService.finalizeResult(event).ifPresent(delivery -> {
                        callbackClient.post(delivery.callbackUrl(), delivery.callback(), UUID.randomUUID(), delivery.correlationId());
                        log.log(requestId, delivery.correlationId(), "Response to CRM", "END",
                                result.success() ? "SUCCESS" : "FAILED", null, delivery.callback(), result.errorMessage());
                    });
                });
    }

    private void completeOrder(Long orderId, Long universalProductKey, ProductResultEvent event) {
        if (productOrderFinalizationService.complete(orderId, universalProductKey, event)) {
            log.log(orderId, event.correlationId(), "CRM callback queued", "END", "SUCCESS", null,
                    new ProductOrderCallback(orderId, "COMPLETED", null), null);
        }
    }

    private void validateEnvelope(UUID eventId, String eventType, int eventVersion, UUID correlationId,
                                  UUID causationId, String producer, Instant occurredAt, Object payload,
                                  String expectedType) {
        if (eventId == null || correlationId == null || causationId == null || occurredAt == null
                || producer == null || producer.isBlank() || payload == null) {
            throw new ProtocolFailureException("Message envelope is missing required fields");
        }
        if (!expectedType.equals(eventType)) {
            throw new ProtocolFailureException("Unsupported event type: " + eventType);
        }
        boolean supportedProductVersion = (eventVersion == 2 || eventVersion == 3)
                && ("ProductCommand".equals(expectedType) || "ProductResult".equals(expectedType));
        if (eventVersion != 1 && !supportedProductVersion) {
            throw new ProtocolFailureException("Unsupported " + eventType + " version: " + eventVersion);
        }
    }

    private List<ProductCommandItem> resolveProducts(Long orderId, UUID correlationId, List<ProductRequest> products) {
        log.log(orderId, correlationId, "Request to Catalog", "START", "SUCCESS", products, null, null);
        var lookupReq = new ProductLookupRequest(products.stream().map(ProductRequest::sourceProductCode).toList());
        var catalogResponse = callCatalog(() -> rest.post()
                        .uri(integrations.getCatalogBaseUrl() + "/api/catalog/lookup")
                        .header("X-Internal-Api-Key", integrations.getInternalApiKey())
                        .body(lookupReq)
                        .retrieve()
                        .body(ProductLookupResponse.class),
                "product lookup");
        validateCatalogResponse(catalogResponse);
        log.log(orderId, correlationId, "Response from Catalog", "END", "SUCCESS", lookupReq, catalogResponse, null);

        Map<String, ProductMapItem> productMap = new HashMap<>();
        catalogResponse.products().forEach(product -> {
            if (productMap.put(product.sourceProductCode(), product) != null) {
                throw new ProtocolFailureException(
                        "Catalog returned duplicate mappings for source product code: " + product.sourceProductCode());
            }
        });
        return products.stream().map(product -> {
            var mapped = productMap.get(product.sourceProductCode());
            if (mapped == null) {
                throw new BusinessFailureException(
                        "Catalog translation missing for source product code: " + product.sourceProductCode());
            }
            if (!product.productType().equals(mapped.productType())) {
                throw new ProtocolFailureException("Requested product type does not match Catalog for code: "
                        + product.sourceProductCode());
            }
            return new ProductCommandItem(product.sourceProductCode(), mapped.targetProductCode(),
                    product.sourceItemRef(), mapped.productType(), mapped.productVersion(), mapped.validityType(),
                    mapped.validityAmount(), mapped.validityUnit());
        }).toList();
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
                    || isBlank(product.targetProductCode())
                    || isBlank(product.productType())
                    || product.productVersion() == null
                    || isBlank(product.validityType())) {
                throw new ProtocolFailureException("Catalog lookup response contains an invalid mapping");
            }
        }
    }

    private void validateProductResult(ProductResult result, int eventVersion) {
        if (result == null || result.orderId() == null) {
            throw new ProtocolFailureException("Subscriber product result has no order ID");
        }
        ProductOrderAction action = productAction(result.action(), eventVersion);
        validateProductActionVersion(action, eventVersion);
        if (action == ProductOrderAction.REMOVE
                && (result.productInstanceId() == null || result.productInstanceId() <= 0)) {
            throw new ProtocolFailureException("REMOVE result has no valid product instance ID");
        }
        if (action != ProductOrderAction.REMOVE && result.productInstanceId() != null) {
            throw new ProtocolFailureException(action + " result unexpectedly contains a removed product instance ID");
        }
        if (action == ProductOrderAction.CHANGE
                && (result.existingProductInstanceId() == null || result.existingProductInstanceId() <= 0)) {
            throw new ProtocolFailureException("CHANGE result has no valid existing tariff instance ID");
        }
        if (action != ProductOrderAction.CHANGE && result.existingProductInstanceId() != null) {
            throw new ProtocolFailureException(action + " result unexpectedly contains an existing tariff instance ID");
        }
        if (!result.success()) {
            if (isBlank(result.errorMessage())) {
                throw new ProtocolFailureException("Failed subscriber product result has no error message");
            }
            return;
        }
        if (result.items() == null) {
            throw new ProtocolFailureException("Successful subscriber product result has no items collection");
        }
        if ((action == ProductOrderAction.ADD || action == ProductOrderAction.CHANGE) && result.items().isEmpty()) {
            throw new ProtocolFailureException("Successful subscriber product result has no items");
        }
        if (action == ProductOrderAction.REMOVE && !result.items().isEmpty()) {
            throw new ProtocolFailureException("Successful REMOVE result must not contain activation mapping items");
        }
        if (action == ProductOrderAction.CHANGE
                && (result.items().size() != 1 || !"TARIFF".equals(result.items().getFirst().productType()))) {
            throw new ProtocolFailureException("Successful CHANGE result must contain exactly one tariff item");
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

    private void validateProductResultMatchesOrder(ProductResult result, int eventVersion, ProductOrder order) {
        ProductOrderAction action = productAction(result.action(), eventVersion);
        if (order.getAction() != action
                || !java.util.Objects.equals(order.getProductInstanceId(), result.productInstanceId())
                || !java.util.Objects.equals(order.getExistingProductInstanceId(), result.existingProductInstanceId())
                || !order.getCustomerId().equals(result.customerId())) {
            throw new ProtocolFailureException("Subscriber product result does not match the stored order identity");
        }
    }

    private ProductOrderAction productAction(ProductOrderAction action, int eventVersion) {
        if (action != null) return action;
        if (eventVersion == 1) return ProductOrderAction.ADD;
        throw new ProtocolFailureException("Product message has no order action");
    }

    private void validateProductActionVersion(ProductOrderAction action, int eventVersion) {
        boolean valid = (eventVersion == 1 && action == ProductOrderAction.ADD)
                || (eventVersion == 2 && action == ProductOrderAction.REMOVE)
                || (eventVersion == 3 && action == ProductOrderAction.CHANGE);
        if (!valid) {
            throw new ProtocolFailureException(
                    "Product result action " + action + " is invalid for version " + eventVersion);
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

    private void failOrder(Long orderId, String error, ProductResultEvent event) {
        if (productOrderFinalizationService.failResult(orderId, error, event)) {
            log.log(orderId, event.correlationId(), "CRM callback queued", "END", "FAILED", null,
                    new ProductOrderCallback(orderId, "FAILED", error), error);
        }
    }

    private void completeCustomer(Long requestId, UUID correlationId) {
        var request = customerRepo.findById(requestId).orElseThrow();
        if (!"IN_PROGRESS".equals(request.getStatus())) return;
        request.setStatus("COMPLETED");
        customerRepo.save(request);

        var callback = new CustomerCallback(requestId, "COMPLETED", null);
        callbackClient.post(request.getCrmCallbackUrl(), callback, UUID.randomUUID(), correlationId);
        log.log(requestId, "Response to CRM", "END", "SUCCESS", null, callback, null);
    }

    private void failCustomer(Long requestId, String error) {
        var request = customerRepo.findById(requestId).orElseThrow();
        if (!"IN_PROGRESS".equals(request.getStatus())) return;
        request.setStatus("FAILED");
        request.setErrorMessage(error);
        customerRepo.save(request);

        var callback = new CustomerCallback(requestId, "FAILED", error);
        callbackClient.post(request.getCrmCallbackUrl(), callback, UUID.randomUUID(), request.getCorrelationId());
        log.log(requestId, "Response to CRM", "END", "FAILED", null, callback, error);
    }
}

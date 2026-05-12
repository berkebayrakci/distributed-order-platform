package com.berke.orders.orchestrator.routes;

import com.berke.orders.orchestrator.dto.OrchestratorDtos.*;
import com.berke.orders.orchestrator.repo.CustomerRequestRepository;
import com.berke.orders.orchestrator.repo.ProductOrderRepository;
import com.berke.orders.orchestrator.service.OrchestratorService;
import com.berke.orders.orchestrator.service.TraceLogService;
import lombok.RequiredArgsConstructor;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OrchestrationRoutes extends RouteBuilder {
    private final TraceLogService log;
    private final ProductOrderRepository orderRepo;
    private final CustomerRequestRepository customerRepo;
    private final RestClient rest = RestClient.builder().build();

    @Override
    public void configure() {
        onException(Exception.class)
                .handled(true)
                .process(exchange -> {
                    Object body = exchange.getIn().getBody();
                    Exception exception = exchange.getProperty(org.apache.camel.Exchange.EXCEPTION_CAUGHT, Exception.class);
                    String message = exception == null ? "Unknown orchestration error" : exception.getMessage();

                    if (body instanceof OrchestratorService.ProductOrderEnvelope env) {
                        failOrder(env.orderId(), message);
                    }
                    if (body instanceof OrchestratorService.CustomerEnvelope env) {
                        failCustomer(env.requestId(), message);
                    }
                });

        from("direct:processProductOrder")
            .routeId("crm-to-subscriber-product-route")
            .process(exchange -> {
                    var env = exchange.getIn().getBody(OrchestratorService.ProductOrderEnvelope.class);
                    var req = env.request();
                    Long orderId = env.orderId();

                    log.log(orderId, "Request from CRM", "START", "SUCCESS", req, null, null);

                    log.log(orderId, "Request to Catalog", "START", "SUCCESS", req.products(), null, null);
                    var lookupReq = new ProductLookupRequest(
                            req.products().stream().map(ProductRequest::sourceProductCode).toList()
                    );
                    var catalogResponse = rest.post()
                            .uri("http://localhost:8082/api/catalog/lookup")
                            .body(lookupReq)
                            .retrieve()
                            .body(ProductLookupResponse.class);
                    log.log(orderId, "Response from Catalog", "END", "SUCCESS", lookupReq, catalogResponse, null);

                    Map<String, ProductMapItem> productMap = new HashMap<>();
                    catalogResponse.products().forEach(p -> productMap.put(p.sourceProductCode(), p));

                    var items = req.products().stream().map(product -> {
                        var mapped = productMap.get(product.sourceProductCode());
                        if (mapped == null) {
                            throw new IllegalArgumentException("Catalog translation missing for source product code: " + product.sourceProductCode());
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

        from("spring-rabbitmq:?queues=subscriber.product.result.queue&autoDeclare=false")
            .routeId("subscriber-product-result-route")
            .unmarshal()
            .json(ProductResult.class)
            .process(exchange -> {
                    var result = exchange.getIn().getBody(ProductResult.class);
                    Long orderId = result.orderId();

                    log.log(orderId, "Response from Subscriber", "END", result.success() ? "SUCCESS" : "FAILED", null, result, result.errorMessage());
                    if (!result.success()) {
                        failOrder(orderId, result.errorMessage());
                        return;
                    }

                    log.log(orderId, "Request to Catalog", "START", "SUCCESS", result, null, null);
                    var insertRequest = new RuntimeMappingInsertRequest(
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
                    var insertResponse = rest.post()
                            .uri("http://localhost:8082/api/catalog/runtime-mappings")
                            .body(insertRequest)
                            .retrieve()
                            .body(RuntimeMappingInsertResponse.class);
                    log.log(orderId, "Response from Catalog", "END", "SUCCESS", insertRequest, insertResponse, null);

                    completeOrder(orderId, insertResponse.universalProductKey());
                });

        from("direct:processCustomerRequest")
            .routeId("crm-to-subscriber-customer-route")
            .process(exchange -> {
                    var env = exchange.getIn().getBody(OrchestratorService.CustomerEnvelope.class);
                    var req = env.request();
                    Long requestId = env.requestId();

                    log.log(requestId, "Request from CRM", "START", "SUCCESS", req, null, null);
                    var command = new CustomerCommand(requestId, req.customerId(), req.firstName(), req.lastName());
                    log.log(requestId, "Request to Subscriber", "START", "SUCCESS", command, null, null);
                    exchange.getIn().setBody(command);
                })
            .marshal()
            .json()
            .setHeader("contentType", constant("application/json"))
            .to("spring-rabbitmq:?routingKey=subscriber.customer.command.queue&autoDeclare=false");

        from("spring-rabbitmq:?queues=subscriber.customer.result.queue&autoDeclare=false")
            .routeId("subscriber-customer-result-route")
            .unmarshal()
            .json(CustomerResult.class)
            .process(exchange -> {
                    var result = exchange.getIn().getBody(CustomerResult.class);
                    Long requestId = result.requestId();

                    log.log(requestId, "Response from Subscriber", "END", result.success() ? "SUCCESS" : "FAILED", null, result, result.errorMessage());
                    if (!result.success()) {
                        failCustomer(requestId, result.errorMessage());
                        return;
                    }
                    completeCustomer(requestId);
                });
    }

    private void completeOrder(Long orderId, Long universalProductKey) {
        var order = orderRepo.findById(orderId).orElseThrow();
        order.setStatus("COMPLETED");
        order.setUniversalProductKey(universalProductKey);
        orderRepo.save(order);

        var callback = new ProductOrderCallback(orderId, "COMPLETED", null);
        rest.post().uri(order.getCrmCallbackUrl()).body(callback).retrieve().toBodilessEntity();
        log.log(orderId, "Response to CRM", "END", "SUCCESS", null, callback, null);
    }

    private void failOrder(Long orderId, String error) {
        var order = orderRepo.findById(orderId).orElseThrow();
        order.setStatus("FAILED");
        order.setErrorMessage(error);
        orderRepo.save(order);

        var callback = new ProductOrderCallback(orderId, "FAILED", error);
        rest.post().uri(order.getCrmCallbackUrl()).body(callback).retrieve().toBodilessEntity();
        log.log(orderId, "Response to CRM", "END", "FAILED", null, callback, error);
    }

    private void completeCustomer(Long requestId) {
        var request = customerRepo.findById(requestId).orElseThrow();
        request.setStatus("COMPLETED");
        customerRepo.save(request);

        var callback = new CustomerCallback(requestId, "COMPLETED", null);
        rest.post().uri(request.getCrmCallbackUrl()).body(callback).retrieve().toBodilessEntity();
        log.log(requestId, "Response to CRM", "END", "SUCCESS", null, callback, null);
    }

    private void failCustomer(Long requestId, String error) {
        var request = customerRepo.findById(requestId).orElseThrow();
        request.setStatus("FAILED");
        request.setErrorMessage(error);
        customerRepo.save(request);

        var callback = new CustomerCallback(requestId, "FAILED", error);
        rest.post().uri(request.getCrmCallbackUrl()).body(callback).retrieve().toBodilessEntity();
        log.log(requestId, "Response to CRM", "END", "FAILED", null, callback, error);
    }
}

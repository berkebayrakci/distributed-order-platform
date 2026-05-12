package com.berke.orders.orchestrator.service;

import com.berke.orders.orchestrator.dto.OrchestratorDtos.*;
import com.berke.orders.orchestrator.model.CustomerRequestEntity;
import com.berke.orders.orchestrator.model.ProductOrder;
import com.berke.orders.orchestrator.repo.CustomerRequestRepository;
import com.berke.orders.orchestrator.repo.ProductOrderRepository;
import com.berke.orders.orchestrator.repo.SequenceRepository;
import lombok.RequiredArgsConstructor;
import org.apache.camel.ProducerTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class OrchestratorService {
    private final ProductOrderRepository orderRepo;
    private final CustomerRequestRepository customerRepo;
    private final SequenceRepository seqRepo;
    private final ProducerTemplate producer;

    private final RestClient subscriberClient = RestClient.builder()
            .baseUrl("http://localhost:8083")
            .build();

    @Transactional
    public ProductOrderResponse createOrder(CreateProductOrderRequest req) {
        Long id = seqRepo.nextOperationId();
        orderRepo.save(ProductOrder.builder()
                .orderId(id)
                .customerId(req.customerId())
                .crmCallbackUrl(req.callbackUrl())
                .status("IN_PROGRESS")
                .build());
        producer.asyncSendBody("direct:processProductOrder", new ProductOrderEnvelope(id, req));
        return new ProductOrderResponse(id, "IN_PROGRESS");
    }

    @Transactional
    public CustomerRequestResponse createCustomer(CreateCustomerRequest req) {
        Long id = seqRepo.nextOperationId();
        customerRepo.save(CustomerRequestEntity.builder()
                .requestId(id)
                .customerId(req.customerId())
                .firstName(req.firstName())
                .lastName(req.lastName())
                .crmCallbackUrl(req.callbackUrl())
                .status("IN_PROGRESS")
                .build());
        producer.asyncSendBody("direct:processCustomerRequest", new CustomerEnvelope(id, req));
        return new CustomerRequestResponse(id, "IN_PROGRESS");
    }

    public OperationStatusResponse getOperation(Long id) {
        return orderRepo.findById(id)
                .map(this::toProductOperation)
                .or(() -> customerRepo.findById(id).map(this::toCustomerOperation))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Operation not found: " + id));
    }

    public CustomerView getCustomer(String customerId) {
        try {
            return subscriberClient.get()
                    .uri("/api/subscriber/customers/{customerId}", customerId)
                    .retrieve()
                    .body(CustomerView.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found: " + customerId);
        }
    }

    public void forceCompleteOrder(Long id) {
        var o = orderRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product order not found: " + id));
        o.setStatus("COMPLETED");
        orderRepo.save(o);
    }

    public void abortOrder(Long id, String reason) {
        var o = orderRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product order not found: " + id));
        o.setStatus("FAILED");
        o.setErrorMessage(reason);
        orderRepo.save(o);
    }

    private OperationStatusResponse toProductOperation(ProductOrder order) {
        return new OperationStatusResponse(
                order.getOrderId(),
                "PRODUCT_ORDER",
                order.getCustomerId(),
                order.getStatus(),
                order.getErrorMessage(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    private OperationStatusResponse toCustomerOperation(CustomerRequestEntity request) {
        return new OperationStatusResponse(
                request.getRequestId(),
                "CUSTOMER_CREATE",
                request.getCustomerId(),
                request.getStatus(),
                request.getErrorMessage(),
                request.getCreatedAt(),
                request.getUpdatedAt()
        );
    }

    public record ProductOrderEnvelope(Long orderId, CreateProductOrderRequest request) {}
    public record CustomerEnvelope(Long requestId, CreateCustomerRequest request) {}
}

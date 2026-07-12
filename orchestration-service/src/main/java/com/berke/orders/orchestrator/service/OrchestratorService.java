package com.berke.orders.orchestrator.service;

import com.berke.orders.orchestrator.dto.OrchestratorDtos.*;
import com.berke.orders.orchestrator.config.IntegrationProperties;
import com.berke.orders.orchestrator.model.CustomerRequestEntity;
import com.berke.orders.orchestrator.model.ProductOrder;
import com.berke.orders.orchestrator.model.OrderStatus;
import com.berke.orders.orchestrator.model.ProductOrderAction;
import com.berke.orders.orchestrator.repo.CustomerRequestRepository;
import com.berke.orders.orchestrator.repo.ProductOrderRepository;
import com.berke.orders.orchestrator.repo.SequenceRepository;
import lombok.RequiredArgsConstructor;
import org.apache.camel.ProducerTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrchestratorService {
    private final ProductOrderRepository orderRepo;
    private final CustomerRequestRepository customerRepo;
    private final SequenceRepository seqRepo;
    private final ProducerTemplate producer;
    private final IntegrationProperties integrations;

    private final RestClient subscriberClient = RestClient.builder().build();

    @Transactional
    public ProductOrderResponse createOrder(CreateProductOrderRequest req, UUID correlationId, UUID causationId) {
        validateProductOrder(req);
        Long id = seqRepo.nextOperationId();
        orderRepo.save(ProductOrder.builder()
                .orderId(id)
                .customerId(req.customerId())
                .action(req.action())
                .productInstanceId(req.productInstanceId())
                .terminationReason(req.reason())
                .correlationId(correlationId)
                .crmCallbackUrl(integrations.getCrmBaseUrl() + "/api/orders/callback")
                .status(OrderStatus.IN_PROGRESS)
                .build());
        sendAfterCommit("direct:processProductOrder", new ProductOrderEnvelope(id, correlationId, causationId, req));
        return new ProductOrderResponse(id, "IN_PROGRESS");
    }

    @Transactional
    public CustomerRequestResponse createCustomer(CreateCustomerRequest req, UUID correlationId, UUID causationId) {
        Long id = seqRepo.nextOperationId();
        customerRepo.save(CustomerRequestEntity.builder()
                .requestId(id)
                .customerId(req.customerId())
                .correlationId(correlationId)
                .firstName(req.firstName())
                .lastName(req.lastName())
                .crmCallbackUrl(integrations.getCrmBaseUrl() + "/api/customers/callback")
                .status("IN_PROGRESS")
                .build());
        sendAfterCommit("direct:processCustomerRequest", new CustomerEnvelope(id, correlationId, causationId, req));
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
                    .uri(integrations.getSubscriberBaseUrl() + "/api/subscriber/customers/{customerId}", customerId)
                    .header("X-Internal-Api-Key", integrations.getInternalApiKey())
                    .retrieve()
                    .body(CustomerView.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found: " + customerId);
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Subscriber service is unavailable", e);
        }
    }

    private void sendAfterCommit(String endpoint, Object body) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            producer.asyncSendBody(endpoint, body);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                producer.asyncSendBody(endpoint, body);
            }
        });
    }

    private void validateProductOrder(CreateProductOrderRequest request) {
        if (request.action() == ProductOrderAction.REMOVE) {
            if (request.productInstanceId() == null || request.productInstanceId() <= 0) {
                throw new IllegalArgumentException("Orchestration validation failed: REMOVE requires a positive productInstanceId");
            }
            if (request.reason() == null || request.reason().isBlank()) {
                throw new IllegalArgumentException("Orchestration validation failed: REMOVE requires a termination reason");
            }
            if (!request.products().isEmpty()) {
                throw new IllegalArgumentException("Orchestration validation failed: REMOVE must not contain products");
            }
            return;
        }
        if (request.products().isEmpty()) {
            throw new IllegalArgumentException("Orchestration validation failed: ADD requires at least one product");
        }
        if (request.productInstanceId() != null || request.reason() != null) {
            throw new IllegalArgumentException("Orchestration validation failed: ADD must not contain removal fields");
        }
    }

    private OperationStatusResponse toProductOperation(ProductOrder order) {
        return new OperationStatusResponse(
                order.getOrderId(),
                "PRODUCT_ORDER",
                order.getCustomerId(),
                order.getStatus().name(),
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

    public record ProductOrderEnvelope(Long orderId, UUID correlationId, UUID causationId,
                                       CreateProductOrderRequest request) {
    }

    public record CustomerEnvelope(Long requestId, UUID correlationId, UUID causationId,
                                   CreateCustomerRequest request) {
    }
}

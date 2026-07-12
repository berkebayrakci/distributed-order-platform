package com.berke.orders.crm.service;

import com.berke.orders.crm.dto.CrmDtos.*;
import com.berke.orders.crm.config.IntegrationProperties;
import com.berke.orders.crm.model.ProductOrder;
import com.berke.orders.crm.model.ProductOrderItem;
import com.berke.orders.crm.model.ProductOrderAction;
import com.berke.orders.crm.repo.ProductOrderItemRepository;
import com.berke.orders.crm.repo.ProductOrderRepository;
import com.berke.orders.crm.repo.ProcessedCallbackEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.HashSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CrmOrderService {
    private final ProductOrderRepository orderRepo;
    private final ProductOrderItemRepository itemRepo;
    private final ProcessedCallbackEventRepository processedCallbackRepository;
    private final IntegrationProperties integrations;
    private final RestClient restClient = RestClient.builder().build();

    @Transactional
    public ProductOrderResponse create(CreateProductOrderRequest req, UUID correlationId) {
        validate(req);

        var orchReq = new OrchestratorProductOrderRequest(
                req.customerId(),
                req.action(),
                req.products(),
                req.productInstanceId(),
                req.reason()
        );

        var res = restClient.post()
                .uri(integrations.getOrchestratorBaseUrl() + "/api/orchestrator/orders")
                .header("X-Internal-Api-Key", integrations.getInternalApiKey())
                .header("X-Correlation-Id", correlationId.toString())
                .header("X-Event-Id", UUID.randomUUID().toString())
                .body(orchReq)
                .retrieve()
                .body(ProductOrderResponse.class);

        orderRepo.save(ProductOrder.builder()
                .orderId(res.orderId())
                .customerId(req.customerId())
                .action(req.action())
                .productInstanceId(req.productInstanceId())
                .terminationReason(req.reason())
                .status(res.status())
                .build());

        for (var p : req.products()) {
            itemRepo.save(ProductOrderItem.builder()
                    .orderId(res.orderId())
                    .sourceProductCode(p.sourceProductCode())
                    .sourceItemRef(p.sourceItemRef())
                    .productType(p.productType())
                    .build());
        }

        return res;
    }

    @Transactional
    public void callback(UUID eventId, ProductOrderCallback cb) {
        if (isDuplicate(eventId, "PRODUCT_ORDER", cb.orderId())) return;

        var o = orderRepo.findById(cb.orderId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product order not found: " + cb.orderId()));
        if (!"IN_PROGRESS".equals(o.getStatus())) {
            if (o.getStatus().equals(cb.status())) return;
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Product order is already terminal");
        }
        o.setStatus(cb.status());
        o.setErrorMessage(cb.errorMessage());
        orderRepo.save(o);
    }

    private boolean isDuplicate(UUID eventId, String operationType, Long operationId) {
        if (processedCallbackRepository.insertIfAbsent(eventId, operationType, operationId) == 1) {
            return false;
        }

        var processed = processedCallbackRepository.findByEventId(eventId);
        if (processed == null
                || !operationType.equals(processed.getOperationType())
                || !operationId.equals(processed.getOperationId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Callback event ID was already used for a different operation");
        }
        return true;
    }

    public ProductOrder getLocalProductOrder(Long id) {
        return orderRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product order not found: " + id));
    }

    private void validate(CreateProductOrderRequest request) {
        if (request.action() == ProductOrderAction.REMOVE) {
            if (request.productInstanceId() == null || request.productInstanceId() <= 0) {
                throw new IllegalArgumentException("CRM validation failed: REMOVE requires a positive productInstanceId");
            }
            if (request.reason() == null || request.reason().isBlank()) {
                throw new IllegalArgumentException("CRM validation failed: REMOVE requires a termination reason");
            }
            if (!request.products().isEmpty()) {
                throw new IllegalArgumentException("CRM validation failed: REMOVE must not contain products");
            }
            return;
        }
        if (request.productInstanceId() != null || request.reason() != null) {
            throw new IllegalArgumentException("CRM validation failed: ADD must not contain removal fields");
        }
        List<ProductRequest> products = request.products();
        if (products.isEmpty()) {
            throw new IllegalArgumentException("CRM validation failed: ADD requires at least one product");
        }
        long tariffs = products.stream().filter(p -> "TARIFF".equals(p.productType())).count();
        long campaigns = products.stream().filter(p -> "CAMPAIGN".equals(p.productType())).count();
        var refs = new HashSet<String>();
        if (products.stream().anyMatch(p -> !refs.add(p.sourceItemRef()))) {
            throw new IllegalArgumentException("CRM validation failed: source item references must be unique per order");
        }

        if (tariffs > 1) {
            throw new IllegalArgumentException("CRM validation failed: customer can request only 1 tariff");
        }
        if (campaigns > 1) {
            throw new IllegalArgumentException("CRM validation failed: customer can request only 1 campaign");
        }
    }
}

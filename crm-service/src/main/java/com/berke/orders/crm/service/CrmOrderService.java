package com.berke.orders.crm.service;

import com.berke.orders.crm.dto.CrmDtos.*;
import com.berke.orders.crm.config.IntegrationProperties;
import com.berke.orders.crm.model.ProductOrder;
import com.berke.orders.crm.model.ProductOrderItem;
import com.berke.orders.crm.repo.ProductOrderItemRepository;
import com.berke.orders.crm.repo.ProductOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.HashSet;

@Service
@RequiredArgsConstructor
public class CrmOrderService {
    private final ProductOrderRepository orderRepo;
    private final ProductOrderItemRepository itemRepo;
    private final IntegrationProperties integrations;
    private final RestClient restClient = RestClient.builder().build();

    @Transactional
    public ProductOrderResponse create(CreateProductOrderRequest req) {
        validate(req.products());

        var orchReq = new OrchestratorProductOrderRequest(
                req.customerId(),
                req.products()
        );

        var res = restClient.post()
                .uri(integrations.getOrchestratorBaseUrl() + "/api/orchestrator/orders")
                .header("X-Internal-Api-Key", integrations.getInternalApiKey())
                .body(orchReq)
                .retrieve()
                .body(ProductOrderResponse.class);

        orderRepo.save(ProductOrder.builder()
                .orderId(res.orderId())
                .customerId(req.customerId())
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
    public void callback(ProductOrderCallback cb) {
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

    public ProductOrder getLocalProductOrder(Long id) {
        return orderRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product order not found: " + id));
    }

    private void validate(List<ProductRequest> products) {
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

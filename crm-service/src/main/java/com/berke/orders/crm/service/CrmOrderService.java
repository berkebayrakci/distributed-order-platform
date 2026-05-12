package com.berke.orders.crm.service;

import com.berke.orders.crm.dto.CrmDtos.*;
import com.berke.orders.crm.model.ProductOrder;
import com.berke.orders.crm.model.ProductOrderItem;
import com.berke.orders.crm.repo.ProductOrderItemRepository;
import com.berke.orders.crm.repo.ProductOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CrmOrderService {
    private final ProductOrderRepository orderRepo;
    private final ProductOrderItemRepository itemRepo;
    private final RestClient restClient = RestClient.builder().baseUrl("http://localhost:8084").build();

    public ProductOrderResponse create(CreateProductOrderRequest req) {
        validate(req.products());

        var orchReq = new OrchestratorProductOrderRequest(
                req.customerId(),
                "http://localhost:8081/api/orders/callback",
                req.products()
        );

        var res = restClient.post()
                .uri("/api/orchestrator/orders")
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

    public void callback(ProductOrderCallback cb) {
        var o = orderRepo.findById(cb.orderId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product order not found: " + cb.orderId()));
        o.setStatus(cb.status());
        o.setErrorMessage(cb.errorMessage());
        orderRepo.save(o);
    }

    public ProductOrder getLocalProductOrder(Long id) {
        return orderRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product order not found: " + id));
    }

    private void validate(List<ProductRequest> products) {
        long tariffs = products.stream().filter(p -> p.productType().equals("TARIFF")).count();
        long campaigns = products.stream().filter(p -> p.productType().equals("CAMPAIGN")).count();

        if (tariffs > 1) {
            throw new IllegalArgumentException("CRM validation failed: customer can request only 1 tariff");
        }
        if (campaigns > 1) {
            throw new IllegalArgumentException("CRM validation failed: customer can request only 1 campaign");
        }
    }
}

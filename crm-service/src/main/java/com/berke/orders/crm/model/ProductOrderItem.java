package com.berke.orders.crm.model;
import jakarta.persistence.*;import lombok.*;
@Entity @Table(name="product_order_item") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProductOrderItem{ @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; private Long orderId; private String sourceProductCode; private String sourceItemRef; private String productType; }

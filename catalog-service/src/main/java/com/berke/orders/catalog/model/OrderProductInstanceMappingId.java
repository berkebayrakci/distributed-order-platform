package com.berke.orders.catalog.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class OrderProductInstanceMappingId implements Serializable {
    private Long universalProductKey;
    private String sourceItemRef;
}

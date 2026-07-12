package com.berke.orders.crm.service;

import com.berke.orders.crm.config.IntegrationProperties;
import com.berke.orders.crm.dto.CrmDtos.CustomerCallback;
import com.berke.orders.crm.model.CustomerRequestEntity;
import com.berke.orders.crm.model.ProcessedCallbackEvent;
import com.berke.orders.crm.repo.CustomerRequestRepository;
import com.berke.orders.crm.repo.ProcessedCallbackEventRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrmCustomerServiceCallbackDeduplicationTest {

    @Test
    void customerCallbackUsesTheSameDeduplicationContract() {
        var requestRepository = mock(CustomerRequestRepository.class);
        var processedRepository = mock(ProcessedCallbackEventRepository.class);
        var service = new CrmCustomerService(
                requestRepository, processedRepository, new IntegrationProperties());
        UUID eventId = UUID.randomUUID();
        var request = CustomerRequestEntity.builder()
                .requestId(51L)
                .customerId("customer-1")
                .status("IN_PROGRESS")
                .build();
        when(requestRepository.findById(51L)).thenReturn(Optional.of(request));
        when(processedRepository.insertIfAbsent(eventId, "CUSTOMER_CREATE", 51L))
                .thenReturn(1, 0);
        when(processedRepository.findByEventId(eventId)).thenReturn(
                ProcessedCallbackEvent.builder()
                        .id(2L)
                        .eventId(eventId)
                        .operationType("CUSTOMER_CREATE")
                        .operationId(51L)
                        .processedAt(Instant.now())
                        .build());
        var callback = new CustomerCallback(51L, "COMPLETED", null);

        service.callback(eventId, callback);
        service.callback(eventId, callback);

        assertEquals("COMPLETED", request.getStatus());
        verify(requestRepository, times(1)).save(request);
    }
}

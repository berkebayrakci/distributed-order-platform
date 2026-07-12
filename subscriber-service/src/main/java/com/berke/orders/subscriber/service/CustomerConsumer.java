package com.berke.orders.subscriber.service;

import com.berke.orders.subscriber.dto.SubscriberDtos.*;
import com.berke.orders.subscriber.model.Customer;
import com.berke.orders.subscriber.repo.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Instant;
import java.util.UUID;
import com.berke.orders.subscriber.exception.UnsupportedEventException;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerConsumer {
    private final CustomerRepository repo;
    private final RabbitTemplate rabbit;
    private final InboxService inbox;
    private static final String CONSUMER = "subscriber-customer-command";

    @RabbitListener(queues = "subscriber.customer.command.queue")
    @Transactional
    public void consume(CustomerCommandEvent event) {
        validateEnvelope(event);
        CustomerCommand cmd = event.payload();
        log.info("Subscriber service received customer request {}", cmd.requestId());
        if (!inbox.begin(CONSUMER, event.eventId(), event.eventType(), event.eventVersion(), event.correlationId())) {
            publishAfterCommit(inbox.replay(CONSUMER, event.eventId(), CustomerResultEvent.class));
            return;
        }
        try {
            if (repo.existsById(cmd.customerId())) {
                finish(event, new CustomerResult(cmd.requestId(), cmd.customerId(), true, null));
                return;
            }
            repo.saveAndFlush(Customer.builder().customerId(cmd.customerId()).firstName(cmd.firstName()).lastName(cmd.lastName()).status("ACTIVE").build());
            finish(event, new CustomerResult(cmd.requestId(), cmd.customerId(), true, null));
        } catch (IllegalArgumentException e) {
            log.error("Subscriber customer request failed {}", cmd.requestId(), e);
            finish(event, new CustomerResult(cmd.requestId(), cmd.customerId(), false, e.getMessage()));
        }
    }

    private void finish(CustomerCommandEvent command, CustomerResult result) {
        var event = new CustomerResultEvent(UUID.randomUUID(), "CustomerResult", 1, command.correlationId(),
                command.eventId(), "subscriber-service", Instant.now(), result);
        inbox.storeResult(CONSUMER, command.eventId(), event);
        publishAfterCommit(event);
    }

    private void publishAfterCommit(CustomerResultEvent result) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                rabbit.convertAndSend("subscriber.customer.result.queue", result);
            }
        });
    }

    private void validateEnvelope(CustomerCommandEvent event) {
        if (event == null || event.eventId() == null || event.correlationId() == null || event.causationId() == null
                || event.occurredAt() == null || event.payload() == null || !"CustomerCommand".equals(event.eventType())) {
            throw new UnsupportedEventException("Malformed or unsupported CustomerCommand envelope");
        }
        if (event.eventVersion() != 1) {
            throw new UnsupportedEventException("Unsupported CustomerCommand version: " + event.eventVersion());
        }
    }
}

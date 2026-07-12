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

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerConsumer {
    private final CustomerRepository repo;
    private final RabbitTemplate rabbit;

    @RabbitListener(queues = "subscriber.customer.command.queue")
    @Transactional
    public void consume(CustomerCommand cmd) {
        log.info("Subscriber service received customer request {}", cmd.requestId());
        try {
            if (repo.existsById(cmd.customerId())) {
                publishAfterCommit(new CustomerResult(cmd.requestId(), cmd.customerId(), true, null));
                return;
            }
            repo.saveAndFlush(Customer.builder().customerId(cmd.customerId()).firstName(cmd.firstName()).lastName(cmd.lastName()).status("ACTIVE").build());
            publishAfterCommit(new CustomerResult(cmd.requestId(), cmd.customerId(), true, null));
        } catch (IllegalArgumentException e) {
            log.error("Subscriber customer request failed {}", cmd.requestId(), e);
            publishAfterCommit(new CustomerResult(cmd.requestId(), cmd.customerId(), false, e.getMessage()));
        }
    }

    private void publishAfterCommit(CustomerResult result) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                rabbit.convertAndSend("subscriber.customer.result.queue", result);
            }
        });
    }
}

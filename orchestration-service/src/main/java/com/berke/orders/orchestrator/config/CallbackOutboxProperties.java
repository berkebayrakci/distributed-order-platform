package com.berke.orders.orchestrator.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "callback-outbox")
@Getter
@Setter
public class CallbackOutboxProperties {
    private Duration pollInterval = Duration.ofSeconds(1);
    private int batchSize = 20;
    private int maxAttempts = 6;
    private Duration initialBackoff = Duration.ofSeconds(1);
    private double backoffMultiplier = 2.0;
    private Duration maxBackoff = Duration.ofMinutes(1);
    private Duration processingTimeout = Duration.ofMinutes(2);
}

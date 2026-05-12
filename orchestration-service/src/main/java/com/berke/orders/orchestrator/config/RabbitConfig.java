package com.berke.orders.orchestrator.config;

import org.springframework.amqp.rabbit.connection.*;
import org.springframework.context.annotation.*;

@Configuration
public class RabbitConfig {
    @Bean
    public ConnectionFactory rabbitConnectionFactory() {
        var f = new CachingConnectionFactory("localhost");
        f.setPort(5672);
        f.setUsername("guest");
        f.setPassword("guest");
        return f;
    }
}

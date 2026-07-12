package com.berke.orders.subscriber.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.*;

@Configuration
public class RabbitConfig {
    @Bean
    public Queue productCommandQueue() {
        return QueueBuilder.durable("subscriber.product.command.queue")
                .deadLetterExchange("")
                .deadLetterRoutingKey("subscriber.product.command.dlq")
                .build();
    }

    @Bean
    public Queue productResultQueue() {
        return QueueBuilder.durable("subscriber.product.result.queue")
                .deadLetterExchange("")
                .deadLetterRoutingKey("subscriber.product.result.dlq")
                .build();
    }

    @Bean
    public Queue customerCommandQueue() {
        return QueueBuilder.durable("subscriber.customer.command.queue")
                .deadLetterExchange("")
                .deadLetterRoutingKey("subscriber.customer.command.dlq")
                .build();
    }

    @Bean
    public Queue customerResultQueue() {
        return QueueBuilder.durable("subscriber.customer.result.queue")
                .deadLetterExchange("")
                .deadLetterRoutingKey("subscriber.customer.result.dlq")
                .build();
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf, Jackson2JsonMessageConverter c) {
        RabbitTemplate t = new RabbitTemplate(cf);
        t.setMessageConverter(c);
        return t;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory cf, Jackson2JsonMessageConverter c) {
        var f = new SimpleRabbitListenerContainerFactory();
        f.setConnectionFactory(cf);
        f.setMessageConverter(c);
        return f;
    }
}

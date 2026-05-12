package com.berke.orders.subscriber.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.*;

@Configuration
public class RabbitConfig {
    @Bean
    public Queue productCommandQueue() {
        return new Queue("subscriber.product.command.queue", true);
    }

    @Bean
    public Queue productResultQueue() {
        return new Queue("subscriber.product.result.queue", true);
    }

    @Bean
    public Queue customerCommandQueue() {
        return new Queue("subscriber.customer.command.queue", true);
    }

    @Bean
    public Queue customerResultQueue() {
        return new Queue("subscriber.customer.result.queue", true);
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

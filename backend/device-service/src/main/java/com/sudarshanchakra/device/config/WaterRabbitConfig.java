package com.sudarshanchakra.device.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnBean(ConnectionFactory.class)
public class WaterRabbitConfig {

    // Declare queues so device-service creates them if not yet present
    @Bean public Queue waterLevelQueue() {
        return QueueBuilder.durable("water.level")
            .withArgument("x-dead-letter-exchange", "farm.dead-letter")
            .withArgument("x-message-ttl", 86400000).build();
    }

    @Bean public Queue waterStatusQueue() { return QueueBuilder.durable("water.status").build(); }

    @Bean public Queue motorStatusQueue() {
        return QueueBuilder.durable("motor.status")
            .withArgument("x-dead-letter-exchange", "farm.dead-letter")
            .withArgument("x-message-ttl", 3600000).build();
    }

    @Bean public Queue motorAlertQueue() {
        return QueueBuilder.durable("motor.alert")
            .withArgument("x-dead-letter-exchange", "farm.dead-letter")
            .withArgument("x-message-ttl", 86400000).build();
    }

    @Bean public MessageConverter jsonMessageConverter() { return new Jackson2JsonMessageConverter(); }

    @Bean public RabbitTemplate rabbitTemplate(ConnectionFactory cf) {
        RabbitTemplate t = new RabbitTemplate(cf);
        t.setMessageConverter(jsonMessageConverter());
        return t;
    }
}

package com.sudarshanchakra.mdm.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_MDM_COMMANDS = "farm.mdm.commands";
    public static final String QUEUE_MDM_TELEMETRY = "mdm.telemetry";

    @Bean
    public TopicExchange mdmCommandExchange() {
        return new TopicExchange(EXCHANGE_MDM_COMMANDS);
    }

    @Bean
    public Queue mdmTelemetryQueue() {
        return QueueBuilder.durable(QUEUE_MDM_TELEMETRY).build();
    }
}

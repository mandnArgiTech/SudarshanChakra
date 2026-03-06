package com.sudarshanchakra.alert.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@ConditionalOnBean(ConnectionFactory.class)
public class RabbitMQConfig {

    public static final String EXCHANGE_ALERTS = "farm.alerts";
    public static final String EXCHANGE_COMMANDS = "farm.commands";
    public static final String EXCHANGE_DEAD_LETTER = "farm.dead-letter";

    public static final String QUEUE_CRITICAL = "alert.critical";
    public static final String QUEUE_HIGH = "alert.high";
    public static final String QUEUE_WARNING = "alert.warning";

    @Bean
    public TopicExchange alertsExchange() {
        return new TopicExchange(EXCHANGE_ALERTS, true, false);
    }

    @Bean
    public DirectExchange commandsExchange() {
        return new DirectExchange(EXCHANGE_COMMANDS, true, false);
    }

    @Bean
    public FanoutExchange deadLetterExchange() {
        return new FanoutExchange(EXCHANGE_DEAD_LETTER, true, false);
    }

    @Bean
    public Queue criticalQueue() {
        return QueueBuilder.durable(QUEUE_CRITICAL)
                .withArgument("x-dead-letter-exchange", EXCHANGE_DEAD_LETTER)
                .withArgument("x-max-priority", 10)
                .withArgument("x-message-ttl", 86400000)
                .build();
    }

    @Bean
    public Queue highQueue() {
        return QueueBuilder.durable(QUEUE_HIGH)
                .withArgument("x-dead-letter-exchange", EXCHANGE_DEAD_LETTER)
                .withArgument("x-message-ttl", 86400000)
                .build();
    }

    @Bean
    public Queue warningQueue() {
        return QueueBuilder.durable(QUEUE_WARNING)
                .withArgument("x-dead-letter-exchange", EXCHANGE_DEAD_LETTER)
                .withArgument("x-message-ttl", 43200000)
                .build();
    }

    @Bean
    public Binding criticalBinding(Queue criticalQueue, TopicExchange alertsExchange) {
        return BindingBuilder.bind(criticalQueue).to(alertsExchange).with("farm.alerts.critical");
    }

    @Bean
    public Binding highBinding(Queue highQueue, TopicExchange alertsExchange) {
        return BindingBuilder.bind(highQueue).to(alertsExchange).with("farm.alerts.high");
    }

    @Bean
    public Binding warningBinding(Queue warningQueue, TopicExchange alertsExchange) {
        return BindingBuilder.bind(warningQueue).to(alertsExchange).with("farm.alerts.warning");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}

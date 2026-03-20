package com.sudarshanchakra.alert.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitMQConfigTest {

    @Test
    void exchangeConstants() {
        assertThat(RabbitMQConfig.EXCHANGE_ALERTS).isEqualTo("farm.alerts");
        assertThat(RabbitMQConfig.EXCHANGE_COMMANDS).isEqualTo("farm.commands");
        assertThat(RabbitMQConfig.EXCHANGE_WATER).isEqualTo("farm.water");
        assertThat(RabbitMQConfig.QUEUE_WATER_LEVEL).isEqualTo("water.level");
    }

    @Test
    void queueConstants() {
        assertThat(RabbitMQConfig.QUEUE_CRITICAL).isEqualTo("alert.critical");
        assertThat(RabbitMQConfig.QUEUE_HIGH).isEqualTo("alert.high");
        assertThat(RabbitMQConfig.QUEUE_WARNING).isEqualTo("alert.warning");
    }
}

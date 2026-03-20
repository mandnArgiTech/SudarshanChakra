package com.sudarshanchakra.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayWebTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void actuatorHealth_returnsOk() {
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void actuatorInfo_returnsOk() {
        webTestClient.get().uri("/actuator/info")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void unknownPath_returns404() {
        webTestClient.get().uri("/nonexistent-route-xyz-12345")
                .exchange()
                .expectStatus().isNotFound();
    }
}

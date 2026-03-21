package com.sudarshanchakra.alert.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // "/ws/alerts" is the canonical path. "/ws" is kept for older dashboards / SockJS base URLs
        // that request /ws/info (would otherwise hit static resource handler and spam logs).
        registry.addEndpoint("/ws/alerts", "/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}

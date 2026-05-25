package com.joselumartos.jwtauthbackenddemo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class StompWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // La puerta de entrada. Angular se conectará a ws://localhost:8080/ws-iot
        registry.addEndpoint("/ws-iot").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // El prefijo para escuchar datos (Backend -> Angular)
        registry.enableSimpleBroker("/topic");
        // El prefijo para enviar peticiones (Angular -> Backend)
        registry.setApplicationDestinationPrefixes("/app");
    }
}

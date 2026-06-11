package com.joselumartos.jwtauthbackenddemo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class StompWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    // Misma lista de orígenes que el filtro CORS de SecurityConfig.
    // El handshake WebSocket aplica su propia validación de Origin independiente.
    @Value("${app.cors.allowed-origins:http://localhost:4200}")
    private String corsAllowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = corsAllowedOrigins.split(",");
        registry.addEndpoint("/ws-iot").setAllowedOriginPatterns(origins);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // El prefijo para escuchar datos (Backend -> Angular)
        registry.enableSimpleBroker("/topic");
        // El prefijo para enviar peticiones (Angular -> Backend)
        registry.setApplicationDestinationPrefixes("/app");
    }
}

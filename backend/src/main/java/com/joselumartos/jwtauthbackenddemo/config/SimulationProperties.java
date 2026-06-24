package com.joselumartos.jwtauthbackenddemo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "simulation")
public record SimulationProperties(
        boolean enabled,
        long intervalMs
) {}

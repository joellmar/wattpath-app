package com.joselumartos.jwtauthbackenddemo.dtos;

import java.time.LocalDateTime;

public record AlertDto(
        Long id,
        String macAddress,
        String username,
        String type,
        String message,
        LocalDateTime createdAt
) { }

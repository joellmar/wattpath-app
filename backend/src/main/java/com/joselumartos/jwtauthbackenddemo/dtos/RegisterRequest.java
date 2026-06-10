package com.joselumartos.jwtauthbackenddemo.dtos;

public record RegisterRequest(
        String username,
        String password,
        String confirmPassword,
        Long tariffId
) {
}

package com.joselumartos.jwtauthbackenddemo.dtos;

public record RegisterRequest(
        String username,
        String password,
        Long tariffId
) {
}

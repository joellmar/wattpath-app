package com.joselumartos.jwtauthbackenddemo.dtos;

public record DeviceDto(
        Long id,
        String username,
        String name,
        String macAddress,
        Boolean isOn,
        Boolean simulated
) {}

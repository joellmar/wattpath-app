package com.joselumartos.jwtauthbackenddemo.dtos;

import java.math.BigDecimal;
import java.time.Instant;

public record ReadingResponse(
        Instant time,
        String macAddress,
        BigDecimal powerW,
        BigDecimal energyTotalKwh,
        Boolean isOn
) {}

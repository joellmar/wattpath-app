package com.joselumartos.jwtauthbackenddemo.dtos;

import java.math.BigDecimal;
import java.util.List;

public record TariffDto(
        Long id,
        String name,
        String type,
        String market,
        BigDecimal contractedPowerKw,
        String energyCompany,
        List<PeriodDto> periods
) {}

package com.joselumartos.jwtauthbackenddemo.dtos;

import java.util.List;

public record TariffDto(
        Long id,
        String name,
        String market,
        // Peaje de acceso (2.0TD, 3.0TD, 6.1TD, 6.2TD). Única fuente de verdad del tipo de tarifa.
        String accessTariffCode,
        // Zona geográfica española, necesaria para discriminar temporadas regulatorias.
        String geographicZone,
        String energyCompany,
        List<PeriodDto> periods,
        List<TariffContractedPowerDto> contractedPowers
) {}

package com.joselumartos.jwtauthbackenddemo.dtos;

import com.joselumartos.jwtauthbackenddemo.entities.TariffContractedPower;

import java.math.BigDecimal;

/**
 * DTO for {@link TariffContractedPower}.
 * Representa la potencia contratada (kW) para un periodo P1-P6 dentro de una tarifa.
 */
public record TariffContractedPowerDto(
        Long id,
        String periodCode,
        BigDecimal contractedPowerKw
) { }

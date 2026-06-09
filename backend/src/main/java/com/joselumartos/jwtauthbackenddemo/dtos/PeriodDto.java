package com.joselumartos.jwtauthbackenddemo.dtos;

import com.joselumartos.jwtauthbackenddemo.entities.Period;

import java.math.BigDecimal;

/**
 * DTO for {@link Period}
 * Solo transporta los datos contractuales del precio: código de periodo y precio €/kWh.
 * Los campos horarios y estacionales vivían aquí antes; ahora residen en tariff_calendar_slots.
 */
public record PeriodDto(
        Long id,
        String periodCode,
        BigDecimal priceKwh
) { }

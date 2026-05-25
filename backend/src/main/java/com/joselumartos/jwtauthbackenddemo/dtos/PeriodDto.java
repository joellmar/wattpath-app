package com.joselumartos.jwtauthbackenddemo.dtos;

import com.joselumartos.jwtauthbackenddemo.entities.DayType;
import com.joselumartos.jwtauthbackenddemo.entities.Period;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * DTO for {@link Period}
 */
public record PeriodDto(
        Long id,
        String name,
        BigDecimal priceKwh,
        LocalTime startHour,
        LocalTime endHour,
        DayType dayType,
        Integer startMonth,
        Integer endMonth
) { }
package com.joselumartos.jwtauthbackenddemo.repositories;

import com.joselumartos.jwtauthbackenddemo.entities.TariffCalendarSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalTime;
import java.util.Optional;

@Repository
public interface TariffCalendarSlotRepository extends JpaRepository<TariffCalendarSlot, Long> {

    /**
     * Devuelve el period_code (P1-P6) para un timestamp local ya descompuesto en
     * accessTariffCode + zona + mes + tipo_día + hora_local.
     *
     * El discriminador accessTariffCode es obligatorio porque 2.0TD y 3.0TD mapean
     * los mismos bloques horarios a periodos distintos (P1-P3 vs P1-P6).
     *
     * Semántica del intervalo: [startTime, endTime).
     * Excepción tipo D (P6 día completo): se admite startTime == endTime.
     */
    @Query("""
            SELECT cs.periodCode FROM TariffCalendarSlot cs
            WHERE cs.accessTariffCode = :accessTariffCode
              AND cs.geographicZone   = :zone
              AND cs.monthNumber      = :month
              AND cs.dayType          = :dayType
              AND (
                    (cs.startTime <> cs.endTime AND cs.startTime <= :localTime AND cs.endTime > :localTime)
                    OR
                    (cs.startTime = cs.endTime AND cs.dayType = 'D')
                    OR (cs.endTime = :endOfDay AND :localTime >= cs.startTime)
                  )
            """)
    Optional<String> findPeriodCode(
            @Param("accessTariffCode") String accessTariffCode,
            @Param("zone")             String zone,
            @Param("month")            int month,
            @Param("dayType")          String dayType,
            @Param("localTime")        LocalTime localTime,
            // JPQL no admite literales string para LocalTime; se pasa el tope del día como parámetro tipado
            @Param("endOfDay")         LocalTime endOfDay
    );

    /**
     * Devuelve el tipo_día laborable (A, B, B1, C) para un peaje + zona + mes concretos.
     * La temporada es única por combinación accessTariffCode + zona + mes.
     */
    @Query("""
            SELECT DISTINCT cs.dayType FROM TariffCalendarSlot cs
            WHERE cs.accessTariffCode = :accessTariffCode
              AND cs.geographicZone   = :zone
              AND cs.monthNumber      = :month
              AND cs.dayType          <> 'D'
            """)
    Optional<String> findWorkdayType(
            @Param("accessTariffCode") String accessTariffCode,
            @Param("zone")             String zone,
            @Param("month")            int month
    );
}

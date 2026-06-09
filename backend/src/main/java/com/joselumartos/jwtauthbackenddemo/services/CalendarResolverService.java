package com.joselumartos.jwtauthbackenddemo.services;

import com.joselumartos.jwtauthbackenddemo.entities.Period;
import com.joselumartos.jwtauthbackenddemo.entities.Tariff;
import com.joselumartos.jwtauthbackenddemo.repositories.PeriodRepository;
import com.joselumartos.jwtauthbackenddemo.repositories.TariffCalendarSlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Resuelve qué Period (precio €/kWh) aplica a un Instant dado para una Tariff concreta.
 *
 * Algoritmo de dos pasos:
 *  1. Convertir Instant a hora local según la zona geográfica del contrato.
 *  2. Determinar el day_type (D para fin de semana; A/B/B1/C para laborable).
 *  3. Consultar tariff_calendar_slots para obtener el period_code.
 *  4. Consultar periods para devolver el precio contractual del periodo.
 *
 * Si tariff_calendar_slots todavía está vacía (MVP sin seed cargado), devuelve Optional.empty()
 * y los servicios llamantes devolverán coste 0 de forma degradada sin lanzar excepción.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CalendarResolverService {

    private final TariffCalendarSlotRepository calendarSlotRepository;
    private final PeriodRepository periodRepository;

    /**
     * Convierte la zona geográfica del contrato en el ZoneId de Java correspondiente.
     * Canarias es UTC+0 (Atlantic/Canary); el resto de zonas españolas usa Europe/Madrid.
     * Se expone como API pública para que ConsumptionService no codifique directamente
     * la decisión Canarias → Atlantic/Canary.
     */
    public ZoneId resolveZoneIdForTariff(Tariff tariff) {
        return resolveZoneId(tariff.getGeographicZone());
    }

    @Transactional(readOnly = true)
    public Optional<Period> resolveApplicablePeriod(Tariff tariff, Instant instant) {
        String accessTariffCode = tariff.getAccessTariffCode();
        String zone             = tariff.getGeographicZone();
        ZoneId zoneId           = resolveZoneId(zone);
        ZonedDateTime zdt       = instant.atZone(zoneId);

        int month      = zdt.getMonthValue();
        LocalTime time = zdt.toLocalTime();
        String dayType = resolveDayType(accessTariffCode, zone, month, zdt.getDayOfWeek());

        // LocalTime.of(23,59) coincide con el end_time del último slot del seed SQL
        // (PostgreSQL TIME no admite 24:00, por eso el seed guarda '23:59' en lugar de medianoche).
        Optional<String> periodCodeOpt =
                calendarSlotRepository.findPeriodCode(accessTariffCode, zone, month, dayType, time, LocalTime.of(23, 59));
        if (periodCodeOpt.isEmpty()) {
            log.debug("No se encontró slot de calendario para peaje={} zona={} mes={} dayType={} hora={}",
                    accessTariffCode, zone, month, dayType, time);
            return Optional.empty();
        }

        String periodCode = periodCodeOpt.get();
        return periodRepository.findByTariffIdAndPeriodCode(tariff.getId(), periodCode);
    }

    /**
     * Determina el day_type para un día concreto.
     * Sábados y domingos siempre son tipo 'D' (P6 todo el día).
     * Para laborables, consulta tariff_calendar_slots filtrando también por accessTariffCode
     * para distinguir la temporada de 2.0TD vs 3.0TD en la misma zona y mes.
     */
    private String resolveDayType(String accessTariffCode, String zone, int month, DayOfWeek dow) {
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return "D";
        }

        // La letra de temporada (A/B/B1/C) se lee del seed; fallback 'C' si el seed no está cargado.
        return calendarSlotRepository.findWorkdayType(accessTariffCode, zone, month).orElse("C");
    }

    /**
     * Mapea código de zona geográfica española a ZoneId para conversión de Instant a hora local.
     * Canarias usa UTC+0 en invierno (Atlantic/Canary), el resto de zonas usa Europe/Madrid.
     */
    private ZoneId resolveZoneId(String geographicZone) {
        if ("CANARIAS".equalsIgnoreCase(geographicZone)) {
            return ZoneId.of("Atlantic/Canary");
        }
        return ZoneId.of("Europe/Madrid");
    }
}

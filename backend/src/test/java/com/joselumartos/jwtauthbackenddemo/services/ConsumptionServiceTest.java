package com.joselumartos.jwtauthbackenddemo.services;

import com.joselumartos.jwtauthbackenddemo.entities.Device;
import com.joselumartos.jwtauthbackenddemo.entities.Period;
import com.joselumartos.jwtauthbackenddemo.entities.Reading;
import com.joselumartos.jwtauthbackenddemo.entities.Tariff;
import com.joselumartos.jwtauthbackenddemo.entities.UserEntity;
import com.joselumartos.jwtauthbackenddemo.repositories.DeviceRepository;
import com.joselumartos.jwtauthbackenddemo.repositories.ReadingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de ConsumptionService.
 *
 * El escenario crítico es el test de Canarias: un Instant que cae a las 00:30 en Madrid
 * (Europe/Madrid, UTC+1 en enero) pero a las 23:30 en Canarias (Atlantic/Canary, UTC+0 en enero).
 * Con la zona hardcodeada que tenía el servicio antes, ese Instant se contaba erróneamente
 * como consumo fantasma para contratos de Canarias.
 */
@ExtendWith(MockitoExtension.class)
class ConsumptionServiceTest {

    @Mock
    private ReadingRepository readingRepository;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private CalendarResolverService calendarResolverService;

    @InjectMocks
    private ConsumptionService consumptionService;

    private static final String MAC = "AA:BB:CC:DD:EE:FF";

    // Enero 2024 -> Madrid = UTC+1 (CET, horario estándar), Canarias = UTC+0 (WET, horario estándar).
    // UTC 23:30 = 00:30 Madrid (+1) = 23:30 Canarias (+0)
    private static final Instant INSTANT_UTC_23_30 = Instant.parse("2024-01-15T23:30:00Z");
    // UTC 01:30 = 02:30 Madrid = 01:30 Canarias -> ambos dentro de la ventana 00:00-05:59
    private static final Instant INSTANT_UTC_01_30  = Instant.parse("2024-01-15T01:30:00Z");
    // UTC 09:00 = 10:00 Madrid = 09:00 Canarias -> fuera de la ventana en ambas zonas
    private static final Instant INSTANT_UTC_09_00  = Instant.parse("2024-01-15T09:00:00Z");

    private Tariff tariffPeninsula;
    private Tariff tariffCanarias;
    private Period periodP1;
    private Device device;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        tariffPeninsula = new Tariff();
        tariffPeninsula.setAccessTariffCode("2.0TD");
        tariffPeninsula.setGeographicZone("PENINSULA");

        tariffCanarias = new Tariff();
        tariffCanarias.setAccessTariffCode("2.0TD");
        tariffCanarias.setGeographicZone("CANARIAS");

        periodP1 = new Period();
        periodP1.setPeriodCode("P1");
        periodP1.setPriceKwh(new BigDecimal("0.150000"));

        user = new UserEntity();

        device = new Device();
        device.setMacAddress(MAC);
        device.setUser(user);
    }

    // -------------------------------------------------------------------------
    // calculateGhostCost – ventana fantasma (00:00–05:59 hora local del suministro)
    // -------------------------------------------------------------------------

    @Test
    void calculateGhostCost_peninsulaTariff_readingInsideGhostWindow_returnsCost() {
        // UTC 01:30 → Madrid 02:30 → hora 2 → dentro de [0, 6) → DEBE contar
        user.setTariff(tariffPeninsula);
        when(deviceRepository.findByMacAddress(MAC)).thenReturn(Optional.of(device));
        when(readingRepository.findReadingsInInterval(eq(MAC), any(), any()))
                .thenReturn(buildReadings(100.0, 101.0, INSTANT_UTC_01_30));
        when(calendarResolverService.resolveZoneIdForTariff(tariffPeninsula))
                .thenReturn(ZoneId.of("Europe/Madrid"));
        when(calendarResolverService.resolveApplicablePeriod(eq(tariffPeninsula), eq(INSTANT_UTC_01_30)))
                .thenReturn(Optional.of(periodP1));

        BigDecimal result = consumptionService.calculateGhostCost(MAC, Instant.EPOCH, Instant.now());

        // delta = 1.0 kWh, priceKwh = 0.15 → coste = 0.15
        assertThat(result).isEqualByComparingTo(new BigDecimal("0.15"));
    }

    @Test
    void calculateGhostCost_peninsulaTariff_readingOutsideGhostWindow_returnsZero() {
        // UTC 09:00 → Madrid 10:00 → hora 10 → fuera de [0, 6) → NO debe contar
        user.setTariff(tariffPeninsula);
        when(deviceRepository.findByMacAddress(MAC)).thenReturn(Optional.of(device));
        when(readingRepository.findReadingsInInterval(eq(MAC), any(), any()))
                .thenReturn(buildReadings(100.0, 101.0, INSTANT_UTC_09_00));
        when(calendarResolverService.resolveZoneIdForTariff(tariffPeninsula))
                .thenReturn(ZoneId.of("Europe/Madrid"));

        BigDecimal result = consumptionService.calculateGhostCost(MAC, Instant.EPOCH, Instant.now());

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void calculateGhostCost_canaryTariff_instantIsMidnightInMadridButEveningInCanarias_returnsZero() {
        // Escenario clave del bug:
        // UTC 23:30 → Madrid (UTC+1) = 00:30 → hora 0 → ghost con la zona hardcodeada anterior
        // UTC 23:30 → Canarias (UTC+0) = 23:30 → hora 23 → fuera de [0, 6) → correcto
        user.setTariff(tariffCanarias);
        when(deviceRepository.findByMacAddress(MAC)).thenReturn(Optional.of(device));
        when(readingRepository.findReadingsInInterval(eq(MAC), any(), any()))
                .thenReturn(buildReadings(100.0, 101.0, INSTANT_UTC_23_30));
        when(calendarResolverService.resolveZoneIdForTariff(tariffCanarias))
                .thenReturn(ZoneId.of("Atlantic/Canary"));

        BigDecimal result = consumptionService.calculateGhostCost(MAC, Instant.EPOCH, Instant.now());

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void calculateGhostCost_canaryTariff_readingInsideGhostWindowCanaryTime_returnsCost() {
        // UTC 01:30 → Canarias (UTC+0) = 01:30 → hora 1 → dentro de [0, 6) → DEBE contar
        user.setTariff(tariffCanarias);
        when(deviceRepository.findByMacAddress(MAC)).thenReturn(Optional.of(device));
        when(readingRepository.findReadingsInInterval(eq(MAC), any(), any()))
                .thenReturn(buildReadings(50.0, 51.5, INSTANT_UTC_01_30));
        when(calendarResolverService.resolveZoneIdForTariff(tariffCanarias))
                .thenReturn(ZoneId.of("Atlantic/Canary"));
        when(calendarResolverService.resolveApplicablePeriod(eq(tariffCanarias), eq(INSTANT_UTC_01_30)))
                .thenReturn(Optional.of(periodP1));

        BigDecimal result = consumptionService.calculateGhostCost(MAC, Instant.EPOCH, Instant.now());

        // delta = 1.5 kWh * 0.15 €/kWh = 0.225 → redondeado a 0.23
        assertThat(result).isEqualByComparingTo(new BigDecimal("0.23"));
    }

    @Test
    void calculateGhostCost_negativeDelta_ignored() {
        // Si el odómetro baja (reinicio de hardware), el delta es negativo → no suma
        user.setTariff(tariffPeninsula);
        when(deviceRepository.findByMacAddress(MAC)).thenReturn(Optional.of(device));
        when(readingRepository.findReadingsInInterval(eq(MAC), any(), any()))
                .thenReturn(buildReadings(200.0, 100.0, INSTANT_UTC_01_30));
        when(calendarResolverService.resolveZoneIdForTariff(tariffPeninsula))
                .thenReturn(ZoneId.of("Europe/Madrid"));

        BigDecimal result = consumptionService.calculateGhostCost(MAC, Instant.EPOCH, Instant.now());

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void calculateGhostCost_lessThanTwoReadings_returnsZero() {
        when(readingRepository.findReadingsInInterval(eq(MAC), any(), any()))
                .thenReturn(List.of());

        BigDecimal result = consumptionService.calculateGhostCost(MAC, Instant.EPOCH, Instant.now());

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // -------------------------------------------------------------------------
    // Helpers de construcción de datos de prueba
    // -------------------------------------------------------------------------

    /**
     * Construye dos lecturas (previous + current) con los valores de odómetro dados.
     * La lectura "current" lleva el Instant del test; "previous" un segundo antes.
     */
    private List<Reading> buildReadings(double previousKwh, double currentKwh, Instant currentTime) {
        Reading previous = new Reading();
        previous.setEnergyTotalKwh(BigDecimal.valueOf(previousKwh));
        previous.setTime(currentTime.minusSeconds(60));

        Reading current = new Reading();
        current.setEnergyTotalKwh(BigDecimal.valueOf(currentKwh));
        current.setTime(currentTime);

        return List.of(previous, current);
    }
}

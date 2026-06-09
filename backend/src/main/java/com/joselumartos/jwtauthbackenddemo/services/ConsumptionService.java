package com.joselumartos.jwtauthbackenddemo.services;

import com.joselumartos.jwtauthbackenddemo.entities.Device;
import com.joselumartos.jwtauthbackenddemo.entities.Period;
import com.joselumartos.jwtauthbackenddemo.entities.Reading;
import com.joselumartos.jwtauthbackenddemo.entities.Tariff;
import com.joselumartos.jwtauthbackenddemo.repositories.DeviceRepository;
import com.joselumartos.jwtauthbackenddemo.repositories.ReadingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConsumptionService {

    private final ReadingRepository readingRepository;
    private final DeviceRepository deviceRepository;
    private final CalendarResolverService calendarResolverService;

    @Transactional(readOnly = true)
    public BigDecimal calculateCostInPeriod(String macAddress, Instant start, Instant end) {
        List<Reading> readings = readingRepository.findReadingsInInterval(macAddress, start, end);
        if (readings.size() < 2) return BigDecimal.ZERO;

        Optional<Device> deviceOpt = deviceRepository.findByMacAddress(macAddress);
        if (deviceOpt.isEmpty() || deviceOpt.get().getUser() == null || deviceOpt.get().getUser().getTariff() == null) {
            log.warn("No se puede calcular coste: Dispositivo {} sin configuración tarifaria completa.", macAddress);
            return BigDecimal.ZERO;
        }

        Tariff tariff = deviceOpt.get().getUser().getTariff();
        BigDecimal totalCost = BigDecimal.ZERO;

        for (int i = 1; i < readings.size(); i++) {
            Reading current  = readings.get(i);
            Reading previous = readings.get(i - 1);

            Optional<BigDecimal> deltaOpt = calculatePositiveDelta(previous, current);
            if (deltaOpt.isEmpty()) continue;

            Optional<BigDecimal> costOpt = calculateStepCost(tariff, current.getTime(), deltaOpt.get());
            if (costOpt.isEmpty()) continue;

            totalCost = totalCost.add(costOpt.get());
        }

        return totalCost.setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateGhostCost(String macAddress, Instant start, Instant end) {
        List<Reading> readings = readingRepository.findReadingsInInterval(macAddress, start, end);
        if (readings.size() < 2) return BigDecimal.ZERO;

        Optional<Device> deviceOpt = deviceRepository.findByMacAddress(macAddress);
        if (deviceOpt.isEmpty() || deviceOpt.get().getUser() == null || deviceOpt.get().getUser().getTariff() == null) {
            return BigDecimal.ZERO;
        }

        Tariff tariff = deviceOpt.get().getUser().getTariff();
        BigDecimal ghostCost = BigDecimal.ZERO;

        for (int i = 1; i < readings.size(); i++) {
            Reading current  = readings.get(i);
            Reading previous = readings.get(i - 1);

            // La ventana fantasma es 00:00-05:59 hora local del suministro; no el valle P6 regulatorio.
            if (!isGhostWindow(tariff, current.getTime())) continue;

            Optional<BigDecimal> deltaOpt = calculatePositiveDelta(previous, current);
            if (deltaOpt.isEmpty()) continue;

            Optional<BigDecimal> costOpt = calculateStepCost(tariff, current.getTime(), deltaOpt.get());
            if (costOpt.isEmpty()) continue;

            ghostCost = ghostCost.add(costOpt.get());
        }

        return ghostCost.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calcula el coste estimado en euros para una potencia activa (W) registrada en un intervalo de segundos.
     * @param macAddress      Dirección MAC única del dispositivo Shelly.
     * @param powerW          Potencia activa medida en Vatios (W).
     * @param durationSeconds Ventana temporal en segundos (frecuencia de muestreo del mensaje MQTT).
     * @return BigDecimal con el coste financiero calculado con precisión de 6 decimales.
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateInstantaneousCost(String macAddress, double powerW, int durationSeconds) {
        if (powerW <= 0 || durationSeconds <= 0) {
            return BigDecimal.ZERO;
        }

        Optional<Device> deviceOpt = deviceRepository.findByMacAddress(macAddress);
        if (deviceOpt.isEmpty()) {
            log.warn("Dispositivo IoT con MAC {} no registrado en el sistema.", macAddress);
            return BigDecimal.ZERO;
        }

        Device device = deviceOpt.get();
        if (device.getUser() == null || device.getUser().getTariff() == null) {
            log.debug("El dispositivo {} no tiene un usuario asignado o carece de tarifa energética.", macAddress);
            return BigDecimal.ZERO;
        }

        Tariff tariff = device.getUser().getTariff();

        Optional<Period> periodOpt = calendarResolverService.resolveApplicablePeriod(tariff, Instant.now());
        if (periodOpt.isEmpty()) {
            log.warn("No hay periodo de calendario resuelto para tarifa id:{} — comprueba que tariff_calendar_slots tiene datos cargados.", tariff.getId());
            return BigDecimal.ZERO;
        }

        Period currentPeriod = periodOpt.get();

        // Energía (kWh) = (Potencia en W / 1000) * (Duración en horas)
        double durationHours = (double) durationSeconds / 3600.0;
        double kwhCalculated = (powerW / 1000.0) * durationHours;

        BigDecimal energyKwh = BigDecimal.valueOf(kwhCalculated);
        BigDecimal priceKwh  = currentPeriod.getPriceKwh();

        return energyKwh.multiply(priceKwh).setScale(6, RoundingMode.HALF_UP);
    }

    // --- Helpers privados ---

    /**
     * Devuelve el delta positivo de energía entre dos lecturas consecutivas del odómetro.
     * Un delta nulo o no positivo indica nulos en la lectura o reinicio de hardware.
     */
    private Optional<BigDecimal> calculatePositiveDelta(Reading previous, Reading current) {
        if (current.getEnergyTotalKwh() == null || previous.getEnergyTotalKwh() == null) {
            return Optional.empty();
        }
        BigDecimal delta = current.getEnergyTotalKwh().subtract(previous.getEnergyTotalKwh());
        return delta.compareTo(BigDecimal.ZERO) > 0 ? Optional.of(delta) : Optional.empty();
    }

    /**
     * Calcula el coste (€) del delta de energía para el periodo regulatorio aplicable en el instante dado.
     * Devuelve Optional.empty() si el calendario no resuelve el periodo (modo degradado sin seed).
     */
    private Optional<BigDecimal> calculateStepCost(Tariff tariff, Instant currentInstant, BigDecimal deltaKwh) {
        return calendarResolverService.resolveApplicablePeriod(tariff, currentInstant)
                .map(period -> deltaKwh.multiply(period.getPriceKwh()));
    }

    /**
     * Determina si el instante dado cae en la ventana de inactividad 00:00–05:59 hora local del contrato.
     * La zona horaria se delega a CalendarResolverService para que Canarias use Atlantic/Canary
     * y no se confunda con un Instant que es medianoche en Madrid pero las 23h en Canarias.
     */
    private boolean isGhostWindow(Tariff tariff, Instant instant) {
        ZoneId zoneId = calendarResolverService.resolveZoneIdForTariff(tariff);
        int hour = instant.atZone(zoneId).getHour();
        return hour >= 0 && hour < 6;
    }
}

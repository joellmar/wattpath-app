package com.joselumartos.jwtauthbackenddemo.services;

import com.joselumartos.jwtauthbackenddemo.entities.Device;
import com.joselumartos.jwtauthbackenddemo.entities.Reading;
import com.joselumartos.jwtauthbackenddemo.entities.SimulationProfile;
import com.joselumartos.jwtauthbackenddemo.mappers.ReadingResponseMapper;
import com.joselumartos.jwtauthbackenddemo.repositories.ReadingRepository;
import com.joselumartos.jwtauthbackenddemo.simulation.SimulationProfileRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class SimulatedTelemetryProcessor {

    private final ReadingRepository readingRepository;
    private final ReadingService readingService;
    private final TelemetryBroadcaster telemetryBroadcaster;
    private final AlertService alertService;
    private final ReadingResponseMapper readingResponseMapper;
    private final SimulationProfileRegistry profileRegistry;

    // Una transacción por dispositivo: un fallo no revierte el resto del tick.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processDevice(Device device, Instant tickStart, long intervalMs) {
        SimulationProfile profile = device.getSimulationProfile();
        boolean deviceOn = Boolean.TRUE.equals(device.getIsOn());
        // Desfase por dispositivo para evitar colisión de PK (time, device_id) si hay solape de ticks.
        Instant readingTime = tickStart.plusMillis(device.getId() % 1000L);

        BigDecimal powerW = deviceOn
                ? profileRegistry.calculatePowerW(profile, readingTime, device)
                : BigDecimal.ZERO;

        BigDecimal previousKwh = readingRepository
                .findFirstByDeviceMacAddressOrderByTimeDesc(device.getMacAddress())
                .map(Reading::getEnergyTotalKwh)
                .orElse(BigDecimal.ZERO);

        BigDecimal nextKwh = deviceOn
                ? calculateNextEnergyTotal(previousKwh, powerW, intervalMs)
                : previousKwh;

        Reading reading = readingService.saveSimulatedReading(device, readingTime, powerW, nextKwh, deviceOn);

        telemetryBroadcaster.broadcast(readingResponseMapper.toDto(reading));
        alertService.checkPowerThreshold(reading);

        log.debug("Telemetría simulada emitida: mac={} profile={} powerW={} kWh={}",
                device.getMacAddress(), profile, powerW, nextKwh);
    }

    private BigDecimal calculateNextEnergyTotal(BigDecimal previousKwh, BigDecimal powerW, long intervalMs) {
        if (powerW.compareTo(BigDecimal.ZERO) <= 0) {
            return previousKwh;
        }

        BigDecimal intervalSeconds = BigDecimal.valueOf(intervalMs)
                .divide(BigDecimal.valueOf(1000), 8, RoundingMode.HALF_UP);

        return previousKwh.add(
                powerW.multiply(intervalSeconds)
                        .divide(BigDecimal.valueOf(1000), 8, RoundingMode.HALF_UP)
                        .divide(BigDecimal.valueOf(3600), 8, RoundingMode.HALF_UP)
        ).setScale(4, RoundingMode.HALF_UP);
    }
}

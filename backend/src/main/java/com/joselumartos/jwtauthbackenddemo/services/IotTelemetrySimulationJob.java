package com.joselumartos.jwtauthbackenddemo.services;

import com.joselumartos.jwtauthbackenddemo.config.SimulationProperties;
import com.joselumartos.jwtauthbackenddemo.entities.Device;
import com.joselumartos.jwtauthbackenddemo.entities.Reading;
import com.joselumartos.jwtauthbackenddemo.mappers.ReadingResponseMapper;
import com.joselumartos.jwtauthbackenddemo.repositories.DeviceRepository;
import com.joselumartos.jwtauthbackenddemo.repositories.ReadingRepository;
import com.joselumartos.jwtauthbackenddemo.simulation.SimulationProfileRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class IotTelemetrySimulationJob {

    private final DeviceRepository deviceRepository;
    private final ReadingRepository readingRepository;
    private final ReadingService readingService;
    private final TelemetryBroadcaster telemetryBroadcaster;
    private final AlertService alertService;
    private final ReadingResponseMapper readingResponseMapper;
    private final SimulationProfileRegistry profileRegistry;
    private final SimulationProperties simulationProperties;

    // @Transactional abre una sesión JPA que permite la carga lazy de device.getUser()
    // dentro de alertService.checkPowerThreshold().
    @Scheduled(fixedRateString = "${simulation.interval-ms:5000}")
    @Transactional
    public void publishSimulatedTelemetry() {
        if (!simulationProperties.enabled()) {
            return;
        }

        Instant now = Instant.now();
        long intervalMs = simulationProperties.intervalMs();

        for (Device device : deviceRepository.findBySimulatedTrue()) {
            boolean deviceOn = Boolean.TRUE.equals(device.getIsOn());
            BigDecimal powerW = deviceOn
                    ? profileRegistry.calculatePowerW(device.getSimulationProfile(), now, device)
                    : BigDecimal.ZERO;

            BigDecimal previousKwh = readingRepository
                    .findFirstByDeviceMacAddressOrderByTimeDesc(device.getMacAddress())
                    .map(Reading::getEnergyTotalKwh)
                    .orElse(BigDecimal.ZERO);

            BigDecimal nextKwh = deviceOn
                    ? calculateNextEnergyTotal(previousKwh, powerW, intervalMs)
                    : previousKwh;

            Reading reading = readingService.saveSimulatedReading(device, now, powerW, nextKwh, deviceOn);

            telemetryBroadcaster.broadcast(readingResponseMapper.toDto(reading));
            alertService.checkPowerThreshold(reading);

            log.debug("Telemetría simulada emitida: mac={} powerW={} kWh={}",
                    device.getMacAddress(), powerW, nextKwh);
        }
    }

    // Incrementa el odómetro: P(W) * intervalo(s) / 1000 (W→kW) / 3600 (s→h) = kWh añadidos.
    BigDecimal calculateNextEnergyTotal(BigDecimal previousKwh, BigDecimal powerW, long intervalMs) {
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

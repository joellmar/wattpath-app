package com.joselumartos.jwtauthbackenddemo.services;

import com.joselumartos.jwtauthbackenddemo.config.SimulationProperties;
import com.joselumartos.jwtauthbackenddemo.entities.Device;
import com.joselumartos.jwtauthbackenddemo.repositories.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class IotTelemetrySimulationJob {

    private final DeviceRepository deviceRepository;
    private final SimulationProperties simulationProperties;
    private final SimulatedTelemetryProcessor telemetryProcessor;

    @Scheduled(fixedRateString = "${simulation.interval-ms:5000}")
    public void publishSimulatedTelemetry() {
        if (!simulationProperties.enabled()) {
            return;
        }

        Instant tickStart = Instant.now();
        long intervalMs = simulationProperties.intervalMs();
        List<Device> simulatedDevices = deviceRepository.findBySimulatedTrue();

        for (Device device : simulatedDevices) {
            try {
                telemetryProcessor.processDevice(device, tickStart, intervalMs);
            } catch (Exception ex) {
                log.warn("Fallo al simular telemetría del dispositivo id={} mac={}: {}",
                        device.getId(), device.getMacAddress(), ex.getMessage());
            }
        }
    }

    // Visible para tests unitarios del cálculo de odómetro.
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

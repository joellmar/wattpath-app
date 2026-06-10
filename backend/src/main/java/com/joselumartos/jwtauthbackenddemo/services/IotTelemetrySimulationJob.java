package com.joselumartos.jwtauthbackenddemo.services;

import com.joselumartos.jwtauthbackenddemo.entities.Device;
import com.joselumartos.jwtauthbackenddemo.entities.Reading;
import com.joselumartos.jwtauthbackenddemo.mappers.ReadingResponseMapper;
import com.joselumartos.jwtauthbackenddemo.repositories.DeviceRepository;
import com.joselumartos.jwtauthbackenddemo.repositories.ReadingRepository;
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

    // Ejecuta cada 5 segundos. @Transactional abre una sesión JPA que permite
    // la carga lazy de device.getUser() dentro de alertService.checkPowerThreshold().
    @Scheduled(fixedRate = 5000)
    @Transactional
    public void publishSimulatedTelemetry() {
        Instant now = Instant.now();

        for (Device device : deviceRepository.findBySimulatedTrue()) {
            BigDecimal powerW = calculatePowerW(now, device.getId());
            BigDecimal previousKwh = readingRepository
                    .findFirstByDeviceMacAddressOrderByTimeDesc(device.getMacAddress())
                    .map(Reading::getEnergyTotalKwh)
                    .orElse(BigDecimal.ZERO);

            BigDecimal nextKwh = calculateNextEnergyTotal(previousKwh, powerW);
            Reading reading = readingService.saveSimulatedReading(device, now, powerW, nextKwh, true);

            telemetryBroadcaster.broadcast(readingResponseMapper.toDto(reading));
            alertService.checkPowerThreshold(reading);

            log.debug("Telemetría simulada emitida: mac={} powerW={} kWh={}",
                    device.getMacAddress(), powerW, nextKwh);
        }
    }

    // Oscilación senoidal entre ~200 W y ~800 W con ciclo de 60 s.
    // Cada dispositivo tiene un desfase de fase propio para evitar que todos suban y bajen a la vez.
    private BigDecimal calculatePowerW(Instant now, Long deviceId) {
        double seconds = now.getEpochSecond();
        double phase = (deviceId % 10) * Math.PI / 5.0;
        double sineValue = Math.sin(seconds * 2 * Math.PI / 60.0 + phase);
        double powerW = 500.0 + 300.0 * sineValue;
        return BigDecimal.valueOf(powerW).setScale(2, RoundingMode.HALF_UP);
    }

    // Incrementa el odómetro: P(W) * intervalo(s) / 1000 (W→kW) / 3600 (s→h) = kWh añadidos.
    // Se preserva el invariante de que energyTotalKwh es siempre estrictamente creciente.
    private BigDecimal calculateNextEnergyTotal(BigDecimal previousKwh, BigDecimal powerW) {
        return previousKwh.add(
                powerW.multiply(BigDecimal.valueOf(5))
                        .divide(BigDecimal.valueOf(1000), 8, RoundingMode.HALF_UP)
                        .divide(BigDecimal.valueOf(3600), 8, RoundingMode.HALF_UP)
        ).setScale(4, RoundingMode.HALF_UP);
    }
}

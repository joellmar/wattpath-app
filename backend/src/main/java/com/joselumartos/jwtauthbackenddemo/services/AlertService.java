package com.joselumartos.jwtauthbackenddemo.services;

import com.joselumartos.jwtauthbackenddemo.dtos.AlertDto;
import com.joselumartos.jwtauthbackenddemo.entities.*;
import com.joselumartos.jwtauthbackenddemo.mappers.AlertDtoMapper;
import com.joselumartos.jwtauthbackenddemo.repositories.AlertRepository;
import com.joselumartos.jwtauthbackenddemo.repositories.TariffContractedPowerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    private final AlertRepository alertRepository;
    private final AlertDtoMapper alertDtoMapper;
    private final TelemetryBroadcaster telemetryBroadcaster;
    private final CalendarResolverService calendarResolverService;
    private final TariffContractedPowerRepository tariffContractedPowerRepository;

    @Transactional(readOnly = true)
    public List<AlertDto> listAll() {
        return alertDtoMapper.toDtoList(alertRepository.findAll());
    }

    @Transactional(readOnly = true)
    public List<AlertDto> listByUsername(String username) {
        return alertDtoMapper.toDtoList(alertRepository.findByUserUsername(username));
    }

    @Transactional
    public int deleteAlertForUser(Long id, String username) {
        return alertRepository.deleteByIdAndUserUsername(id, username);
    }

    @Transactional
    public void checkPowerThreshold(Reading reading) {
        if (reading == null || reading.getDevice() == null
                || reading.getPowerW() == null || reading.getTime() == null) {
            return;
        }

        Device device = reading.getDevice();
        UserEntity user = device.getUser();

        if (user == null || user.getTariff() == null || user.getTariff().getId() == null) {
            return;
        }

        Tariff tariff = user.getTariff();

        // Paso 1: resolver qué periodo (P1-P6) aplica en el instante de la lectura.
        // Si el calendario regulatorio está vacío (seed no cargado), no generamos alerta.
        Optional<Period> applicablePeriodOpt = calendarResolverService.resolveApplicablePeriod(tariff, reading.getTime());
        if (applicablePeriodOpt.isEmpty()) {
            log.debug("No se comprueba maxímetro: sin periodo calendario para tariffId={} instant={}",
                    tariff.getId(), reading.getTime());
            return;
        }

        String periodCode = applicablePeriodOpt.get().getPeriodCode();

        // Paso 2: obtener la potencia contratada para ese periodo concreto.
        // Sin potencia configurada por periodo, no hay umbral contra el que comparar.
        Optional<TariffContractedPower> contractedPowerOpt =
                tariffContractedPowerRepository.findByTariffIdAndPeriodCode(tariff.getId(), periodCode);
        if (contractedPowerOpt.isEmpty()) {
            log.debug("No se comprueba maxímetro: sin potencia contratada para tariffId={} periodCode={}",
                    tariff.getId(), periodCode);
            return;
        }

        BigDecimal limitPowerKw = contractedPowerOpt.get().getContractedPowerKw();
        if (limitPowerKw == null || limitPowerKw.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal currentPowerKw = reading.getPowerW()
                .divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP);

        if (currentPowerKw.compareTo(limitPowerKw) <= 0) {
            return;
        }

        BigDecimal overloadPercentage = currentPowerKw
                .multiply(BigDecimal.valueOf(100))
                .divide(limitPowerKw, 1, RoundingMode.HALF_UP);

        String message = String.format(
                "¡ALERTA CRÍTICA DE MAXÍMETRO! El dispositivo %s ha registrado un pico de %s kW en %s, " +
                "sobrepasando la potencia contratada de %s kW (%s%% del límite). Riesgo de penalización en factura.",
                device.getName(), currentPowerKw, periodCode, limitPowerKw, overloadPercentage
        );

        log.warn("ICP TRIGGERED: {}", message);

        Alert alert = new Alert();
        alert.setUser(user);
        alert.setDevice(device);
        alert.setType("OVERPOWER");
        alert.setMessage(message);

        Alert savedAlert = alertRepository.save(alert);
        AlertDto alertDto = alertDtoMapper.toDto(savedAlert);

        telemetryBroadcaster.broadcast(alertDto);
    }
}

package com.joselumartos.jwtauthbackenddemo.services;

import com.joselumartos.jwtauthbackenddemo.dtos.AlertDto;
import com.joselumartos.jwtauthbackenddemo.entities.*;
import com.joselumartos.jwtauthbackenddemo.mappers.AlertDtoMapper;
import com.joselumartos.jwtauthbackenddemo.repositories.AlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    private final AlertRepository alertRepository;
    private final AlertDtoMapper alertDtoMapper;
    private final TelemetryBroadcaster telemetryBroadcaster;

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
        if (reading == null || reading.getDevice() == null || reading.getPowerW() == null) {
            return;
        }

        Device device = reading.getDevice();
        UserEntity user = device.getUser();

        if (user == null || user.getTariff() == null || user.getTariff().getContractedPowerKw() == null) {
            return;
        }

        Tariff tariff = user.getTariff();

        BigDecimal currentPowerKw = reading.getPowerW().divide(BigDecimal.valueOf(1000), RoundingMode.HALF_UP);
        BigDecimal limitPowerKw = tariff.getContractedPowerKw();

        if (currentPowerKw.compareTo(limitPowerKw) > 0) {
            BigDecimal overloadPercentage = currentPowerKw.multiply(BigDecimal.valueOf(100)).divide(limitPowerKw, 1, RoundingMode.HALF_UP);

            String message = String.format(
                    "¡ALERTA CRÍTICA DE MAXÍMETRO! El dispositivo %s ha registrado un pico de %s kW, sobrepasando la potencia contratada de %s kW (Sobrecarga del %s%%). Risgo de penalización en factura.",
                    device.getName(), currentPowerKw, limitPowerKw, overloadPercentage
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
}

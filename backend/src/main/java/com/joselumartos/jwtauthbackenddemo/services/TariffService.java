package com.joselumartos.jwtauthbackenddemo.services;

import com.joselumartos.jwtauthbackenddemo.dtos.TariffDto;
import com.joselumartos.jwtauthbackenddemo.entities.Device;
import com.joselumartos.jwtauthbackenddemo.entities.Period;
import com.joselumartos.jwtauthbackenddemo.entities.Tariff;
import com.joselumartos.jwtauthbackenddemo.mappers.TariffDtoMapper;
import com.joselumartos.jwtauthbackenddemo.repositories.DeviceRepository;
import com.joselumartos.jwtauthbackenddemo.repositories.PeriodRepository;
import com.joselumartos.jwtauthbackenddemo.repositories.TariffRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TariffService {
    private final TariffRepository tariffRepository;
    private final PeriodRepository periodRepository;
    private final DeviceRepository deviceRepository;
    private final TariffDtoMapper tariffDtoMapper;

    @Transactional(readOnly = true)
    public List<TariffDto> listAll() {
        return tariffDtoMapper.toDtoList(tariffRepository.findAll());
    }

    @Transactional
    public TariffDto save(Tariff tariff) {
        // Asegurar la relación bidireccional limpia con los periodos para evitar huérfanos en cascada
        if (tariff.getPeriods() != null) {
            tariff.getPeriods().forEach(period -> period.setTariff(tariff));
        }

        Tariff saved = tariffRepository.save(tariff);
        return tariffDtoMapper.toDto(saved);
    }

    @Transactional
    public TariffDto save(TariffDto tariffDto) {
        Tariff tariff = tariffDtoMapper.toEntity(tariffDto);
        // Asegurar la relación bidireccional limpia con los periodos para evitar huérfanos en cascada
        if (tariff.getPeriods() != null) {
            tariff.getPeriods().forEach(period -> period.setTariff(tariff));
        }

        Tariff saved = tariffRepository.save(tariff);
        return tariffDtoMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public TariffDto findById(Long id) {
        return tariffRepository.findById(id)
                .map(tariffDtoMapper::toDto)
                .orElseThrow(() -> new EntityNotFoundException("Tariff not found with id: " + id));
    }

    @Transactional
    public void deleteById(Long id) {
        if (!tariffRepository.existsById(id)) {
            throw new EntityNotFoundException("Tariff not found with id: " + id);
        }

        tariffRepository.deleteById(id);
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

        // 1. Obtener dispositivo por su MAC y validar que tenga dueño y tarifa asociada
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
        LocalTime now = LocalTime.now();

        // 2. Localizar el periodo de discriminación horaria activo
        Optional<Period> periodOpt = periodRepository.findApplicablePeriod(tariff.getId(), now);
        if (periodOpt.isEmpty()) {
            log.warn("¡Inconsistencia de configuración! No hay periodo definido para tarifa id:{} a  las {}", tariff.getId(), now);
            return BigDecimal.ZERO;
        }

        Period currentPeriod = periodOpt.get();

        // 3. Transformación física de Potencia Instantánea (W) a Energía Consumida (kWh)
        // Energía (kWh) = (Potencia en W / 1000) * (Duración en horas)
        double durationHours = (double) durationSeconds / 3600.0;
        double kwhCalculated = (powerW / 1000.0) * durationHours;

        BigDecimal energyKwh = BigDecimal.valueOf(kwhCalculated);
        BigDecimal priceKwh = currentPeriod.getPriceKwh();

        // 4. Coste = Energía (kWh) * Precio del periodo (€/kWh)
        // Escalamos a 6 decimales que es el estándar de precisión que definiste en tu entidad Period para 'priceKwh'
        return energyKwh.multiply(priceKwh).setScale(6, RoundingMode.HALF_UP);
    }

}

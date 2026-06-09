package com.joselumartos.jwtauthbackenddemo.services;

import com.joselumartos.jwtauthbackenddemo.dtos.PeriodDto;
import com.joselumartos.jwtauthbackenddemo.dtos.TariffContractedPowerDto;
import com.joselumartos.jwtauthbackenddemo.dtos.TariffDto;
import com.joselumartos.jwtauthbackenddemo.entities.Device;
import com.joselumartos.jwtauthbackenddemo.entities.Period;
import com.joselumartos.jwtauthbackenddemo.entities.Tariff;
import com.joselumartos.jwtauthbackenddemo.entities.TariffContractedPower;
import com.joselumartos.jwtauthbackenddemo.mappers.TariffDtoMapper;
import com.joselumartos.jwtauthbackenddemo.repositories.DeviceRepository;
import com.joselumartos.jwtauthbackenddemo.repositories.TariffRepository;
import com.joselumartos.jwtauthbackenddemo.repositories.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class TariffService {

    // --- Constantes regulatorias de periodos por peaje ---

    /** Periodos de energía (€/kWh) exigidos para 2.0TD según Circular CNMC 3/2020. */
    private static final Set<String> ENERGY_PERIODS_2_0TD = Set.of("P1", "P2", "P3");

    /** Periodos de potencia contratada (kW) exigidos para 2.0TD (dos términos de potencia). */
    private static final Set<String> POWER_PERIODS_2_0TD = Set.of("P1", "P2");

    /** Periodos P1-P6 exigidos tanto en energía como en potencia para 3.0TD/6.1TD/6.2TD. */
    private static final Set<String> PERIODS_P1_P6 = Set.of("P1", "P2", "P3", "P4", "P5", "P6");

    /**
     * Orden regulatorio para validar P1 <= P2 <= ... <= P6 en peajes de 6 periodos.
     * Se necesita la lista ordenada para comparar pares consecutivos.
     */
    private static final List<String> ORDERED_PERIODS = List.of("P1", "P2", "P3", "P4", "P5", "P6");

    /** Peajes que exigen los 6 periodos independientes por P1-P6. */
    private static final Set<String> MULTI_PERIOD_TARIFF_CODES = Set.of("3.0TD", "6.1TD", "6.2TD");

    // --- Dependencias ---

    private final TariffRepository tariffRepository;
    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final TariffDtoMapper tariffDtoMapper;
    private final CalendarResolverService calendarResolverService;

    // @PersistenceContext no es compatible con la inyección de constructor de Lombok;
    // se usa inyección de campo porque es la única forma estándar con JPA.
    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<TariffDto> listAll() {
        return tariffDtoMapper.toDtoList(tariffRepository.findAll());
    }

    /**
     * Devuelve solo las tarifas del catálogo maestro, excluyendo clones privados de usuarios.
     */
    @Transactional(readOnly = true)
    public List<TariffDto> listCatalog() {
        return tariffDtoMapper.toDtoList(tariffRepository.findAllCatalog());
    }

    @Transactional
    public TariffDto save(Tariff tariff) {
        if (tariff.getPeriods() != null) {
            tariff.getPeriods().forEach(period -> period.setTariff(tariff));
        }
        if (tariff.getContractedPowers() != null) {
            tariff.getContractedPowers().forEach(power -> power.setTariff(tariff));
        }

        validateTariffContract(tariff);

        Tariff saved = tariffRepository.save(tariff);
        return tariffDtoMapper.toDto(saved);
    }

    @Transactional
    public TariffDto save(TariffDto tariffDto) {
        Tariff tariff = tariffDtoMapper.toEntity(tariffDto);
        if (tariff.getPeriods() != null) {
            tariff.getPeriods().forEach(period -> period.setTariff(tariff));
        }
        if (tariff.getContractedPowers() != null) {
            tariff.getContractedPowers().forEach(power -> power.setTariff(tariff));
        }

        validateTariffContract(tariff);

        Tariff saved = tariffRepository.save(tariff);
        return tariffDtoMapper.toDto(saved);
    }

    /**
     * Actualiza una tarifa existente del catálogo en base a su id y un DTO de entrada.
     *
     * El patrón clear + flush + rebuild es obligatorio para evitar la violación de la
     * restricción única (tariff_id, period_code): Hibernate procesa los INSERTs antes
     * que los DELETEs por defecto, así que hay que forzar el DELETE de los huérfanos
     * con un flush explícito antes de añadir los nuevos registros.
     */
    @Transactional
    public TariffDto update(Long id, TariffDto dto) {
        Tariff existing = tariffRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Tarifa no encontrada con id: " + id));

        existing.setName(dto.name());
        existing.setMarket(dto.market());
        existing.setAccessTariffCode(dto.accessTariffCode());
        existing.setGeographicZone(dto.geographicZone());
        existing.setEnergyCompany(dto.energyCompany());

        existing.getPeriods().clear();
        existing.getContractedPowers().clear();
        entityManager.flush();

        if (dto.periods() != null) {
            for (PeriodDto p : dto.periods()) {
                Period period = new Period();
                period.setPeriodCode(p.periodCode());
                period.setPriceKwh(p.priceKwh());
                period.setTariff(existing);
                existing.getPeriods().add(period);
            }
        }

        if (dto.contractedPowers() != null) {
            for (TariffContractedPowerDto cp : dto.contractedPowers()) {
                TariffContractedPower power = new TariffContractedPower();
                power.setPeriodCode(cp.periodCode());
                power.setContractedPowerKw(cp.contractedPowerKw());
                power.setTariff(existing);
                existing.getContractedPowers().add(power);
            }
        }

        validateTariffContract(existing);

        // La entidad está gestionada; no hace falta llamar a save() explícitamente.
        return tariffDtoMapper.toDto(existing);
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
            throw new EntityNotFoundException("Tarifa no encontrada con id: " + id);
        }
        if (userRepository.existsByTariffId(id)) {
            throw new IllegalStateException(
                    "No se puede eliminar esta tarifa porque está asignada a un usuario activo.");
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

        double durationHours = (double) durationSeconds / 3600.0;
        double kwhCalculated = (powerW / 1000.0) * durationHours;

        BigDecimal energyKwh = BigDecimal.valueOf(kwhCalculated);
        BigDecimal priceKwh  = currentPeriod.getPriceKwh();

        return energyKwh.multiply(priceKwh).setScale(6, RoundingMode.HALF_UP);
    }

    // --- Validación de contrato ---

    /**
     * Valida que el contrato de tarifa cumpla los requisitos legales de la Circular CNMC 3/2020
     * antes de persistirlo. Lanza IllegalStateException capturada por GlobalExceptionHandler (HTTP 400).
     *
     * Reglas por peaje:
     *  - 2.0TD:           energía P1-P3, potencia P1-P2.
     *  - 3.0TD/6.1TD/6.2TD: energía P1-P6, potencia P1-P6 con orden P1 <= P2 <= ... <= P6.
     *
     * Es público para que UserTariffService reutilice las mismas reglas regulatorias
     * sin duplicar la lógica de validación.
     */
    public void validateTariffContract(Tariff tariff) {
        String code = tariff.getAccessTariffCode();
        if (code == null || code.isBlank()) {
            throw new IllegalStateException("El código de peaje de acceso (accessTariffCode) es obligatorio.");
        }
        if (tariff.getGeographicZone() == null || tariff.getGeographicZone().isBlank()) {
            throw new IllegalStateException("La zona geográfica (geographicZone) es obligatoria.");
        }

        List<Period> periods = tariff.getPeriods();
        if (periods == null || periods.isEmpty()) {
            throw new IllegalStateException("El contrato debe incluir al menos un periodo de energía.");
        }

        Set<String> energyCodes = new HashSet<>();
        for (Period p : periods) {
            if (!energyCodes.add(p.getPeriodCode())) {
                throw new IllegalStateException(
                        "Código de periodo duplicado en energía: " + p.getPeriodCode() + ".");
            }
            if (p.getPriceKwh() == null || p.getPriceKwh().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException(
                        "El precio €/kWh del periodo " + p.getPeriodCode() + " debe ser mayor que cero.");
            }
        }

        List<TariffContractedPower> contractedPowers = tariff.getContractedPowers();
        if (contractedPowers == null || contractedPowers.isEmpty()) {
            throw new IllegalStateException("El contrato debe incluir al menos una potencia contratada.");
        }

        Set<String> powerCodes = new HashSet<>();
        for (TariffContractedPower cp : contractedPowers) {
            if (!powerCodes.add(cp.getPeriodCode())) {
                throw new IllegalStateException(
                        "Código de periodo duplicado en potencias: " + cp.getPeriodCode() + ".");
            }
            if (cp.getContractedPowerKw() == null || cp.getContractedPowerKw().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException(
                        "La potencia contratada del periodo " + cp.getPeriodCode() + " debe ser mayor que cero.");
            }
        }

        if ("2.0TD".equals(code)) {
            validate20TdPeriods(energyCodes, powerCodes);
        } else if (MULTI_PERIOD_TARIFF_CODES.contains(code)) {
            validate6PeriodTariff(energyCodes, powerCodes, contractedPowers);
        }
    }

    private void validate20TdPeriods(Set<String> energyCodes, Set<String> powerCodes) {
        if (!energyCodes.containsAll(ENERGY_PERIODS_2_0TD)) {
            throw new IllegalStateException(
                    "La tarifa 2.0TD requiere precios de energía para P1, P2 y P3.");
        }
        if (!powerCodes.containsAll(POWER_PERIODS_2_0TD)) {
            throw new IllegalStateException(
                    "La tarifa 2.0TD requiere potencias contratadas para P1 y P2.");
        }
    }

    private void validate6PeriodTariff(Set<String> energyCodes, Set<String> powerCodes,
                                        List<TariffContractedPower> contractedPowers) {
        if (!energyCodes.containsAll(PERIODS_P1_P6)) {
            throw new IllegalStateException(
                    "La tarifa 3.0TD/6.xTD requiere precios de energía para P1, P2, P3, P4, P5 y P6.");
        }
        if (!powerCodes.containsAll(PERIODS_P1_P6)) {
            throw new IllegalStateException(
                    "La tarifa 3.0TD/6.xTD requiere potencias contratadas para P1, P2, P3, P4, P5 y P6.");
        }

        Map<String, BigDecimal> powerByCode = new HashMap<>();
        for (TariffContractedPower cp : contractedPowers) {
            powerByCode.put(cp.getPeriodCode(), cp.getContractedPowerKw());
        }

        for (int i = 0; i < ORDERED_PERIODS.size() - 1; i++) {
            String current = ORDERED_PERIODS.get(i);
            String next    = ORDERED_PERIODS.get(i + 1);
            BigDecimal currentKw = powerByCode.get(current);
            BigDecimal nextKw    = powerByCode.get(next);
            if (currentKw != null && nextKw != null && currentKw.compareTo(nextKw) > 0) {
                throw new IllegalStateException(String.format(
                        "La potencia contratada no cumple el orden legal: %s (%.2f kW) > %s (%.2f kW).",
                        current, currentKw, next, nextKw));
            }
        }
    }
}

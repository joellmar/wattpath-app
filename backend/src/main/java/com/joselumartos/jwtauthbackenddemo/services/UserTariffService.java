package com.joselumartos.jwtauthbackenddemo.services;

import com.joselumartos.jwtauthbackenddemo.dtos.TariffDto;
import com.joselumartos.jwtauthbackenddemo.dtos.UserTariffRequest;
import com.joselumartos.jwtauthbackenddemo.entities.Period;
import com.joselumartos.jwtauthbackenddemo.entities.Tariff;
import com.joselumartos.jwtauthbackenddemo.entities.TariffContractedPower;
import com.joselumartos.jwtauthbackenddemo.entities.UserEntity;
import com.joselumartos.jwtauthbackenddemo.mappers.TariffDtoMapper;
import com.joselumartos.jwtauthbackenddemo.repositories.TariffRepository;
import com.joselumartos.jwtauthbackenddemo.repositories.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserTariffService {

    private final UserRepository userRepository;
    private final TariffRepository tariffRepository;
    private final TariffDtoMapper tariffDtoMapper;
    private final TariffService tariffService;

    /**
     * Devuelve la tarifa privada del usuario autenticado, si tiene una asignada.
     */
    @Transactional(readOnly = true)
    public Optional<TariffDto> getMyTariff(String username) {
        UserEntity user = findUser(username);
        if (user.getTariff() == null) {
            return Optional.empty();
        }
        return Optional.of(tariffDtoMapper.toDto(user.getTariff()));
    }

    /**
     * Asigna o actualiza la tarifa privada del usuario autenticado.
     *
     * Si llega templateTariffId: clona la plantilla del catálogo maestro y aplica
     * los overrides del contract si existen. La plantilla nunca se muta.
     *
     * Si llega solo contract: mapea el DTO directamente a entidad y lo persiste.
     *
     * En ambos casos se validan las reglas regulatorias de la CNMC 3/2020
     * antes de persistir (delegado a TariffService.validateTariffContract).
     */
    @Transactional
    public TariffDto saveMyTariff(String username, UserTariffRequest request) {
        if (request.templateTariffId() == null && request.contract() == null) {
            throw new IllegalStateException("La solicitud debe incluir templateTariffId, un contrato o ambos.");
        }

        UserEntity user = findUser(username);

        // Edición de la tarifa privada ya existente: el contrato llega con su id.
        // Se delega en TariffService.update para evitar la violación de la restricción
        // única (tariff_id, period_code) que ocurre cuando Hibernate procesa INSERTs
        // antes de DELETEs en la misma transacción con colecciones sin flush explícito.
        if (request.templateTariffId() == null
                && request.contract() != null
                && request.contract().id() != null) {

            Long contractId = request.contract().id();
            if (user.getTariff() == null || !user.getTariff().getId().equals(contractId)) {
                throw new IllegalStateException(
                        "Solo puedes modificar tu propia tarifa activa.");
            }
            TariffDto updated = tariffService.update(contractId, request.contract());
            log.info("Tarifa privada actualizada para el usuario '{}', tariff_id={}", username, contractId);
            return updated;
        }

        Tariff tariffToSave;

        if (request.templateTariffId() != null) {
            Tariff template = tariffRepository.findById(request.templateTariffId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Plantilla de tarifa no encontrada: " + request.templateTariffId()));
            tariffToSave = cloneTariff(template);

            if (request.contract() != null) {
                applyContractOverrides(tariffToSave, request.contract());
            }
        } else {
            // Contrato nuevo sin id: primera asignación sin clonar plantilla
            tariffToSave = tariffDtoMapper.toEntity(request.contract());
            if (tariffToSave.getPeriods() != null) {
                tariffToSave.getPeriods().forEach(p -> p.setTariff(tariffToSave));
            }
            if (tariffToSave.getContractedPowers() != null) {
                tariffToSave.getContractedPowers().forEach(cp -> cp.setTariff(tariffToSave));
            }
        }

        tariffService.validateTariffContract(tariffToSave);

        Tariff saved = tariffRepository.save(tariffToSave);
        user.setTariff(saved);
        userRepository.save(user);

        log.info("Tarifa privada asignada al usuario '{}', tariff_id={}", username, saved.getId());
        return tariffDtoMapper.toDto(saved);
    }

    /**
     * Desvincula y elimina la tarifa privada del usuario autenticado.
     *
     * Los clones privados son entidades independientes (nunca la plantilla del catálogo),
     * así que se pueden borrar de forma segura. Si no se eliminan, quedarían huérfanas
     * en la tabla tariffs y reaparecerían en el catálogo al no tener propietario.
     *
     * Orden obligatorio: primero poner user.tariff = null y persistir para liberar la FK,
     * después borrar la entidad Tariff con su cascada (periodos y potencias).
     */
    @Transactional
    public void unlinkMyTariff(String username) {
        UserEntity user = findUser(username);
        Tariff tariff = user.getTariff();
        if (tariff == null) {
            log.debug("Usuario '{}' no tiene tarifa asignada; nada que desvincular.", username);
            return;
        }
        Long tariffId = tariff.getId();
        user.setTariff(null);
        userRepository.save(user);
        tariffRepository.deleteById(tariffId);
        log.info("Tarifa privada desvinculada y eliminada para el usuario '{}', tariff_id={}.", username, tariffId);
    }

    // -------------------------------------------------------------------------
    // Métodos privados de apoyo
    // -------------------------------------------------------------------------

    /**
     * Crea una copia profunda de una tarifa del catálogo para el usuario.
     * No reutiliza ninguna instancia de la plantilla: periodos y potencias son entidades nuevas.
     * Esto evita aliasing JPA y garantiza que la plantilla global nunca sea mutada.
     */
    private Tariff cloneTariff(Tariff template) {
        Tariff clone = new Tariff();
        clone.setName(template.getName());
        clone.setMarket(template.getMarket());
        clone.setAccessTariffCode(template.getAccessTariffCode());
        clone.setGeographicZone(template.getGeographicZone());
        clone.setEnergyCompany(template.getEnergyCompany());

        List<Period> clonedPeriods = new ArrayList<>();
        for (Period p : template.getPeriods()) {
            Period cloned = new Period();
            cloned.setPeriodCode(p.getPeriodCode());
            cloned.setPriceKwh(p.getPriceKwh());
            cloned.setTariff(clone);
            clonedPeriods.add(cloned);
        }
        clone.setPeriods(clonedPeriods);

        List<TariffContractedPower> clonedPowers = new ArrayList<>();
        for (TariffContractedPower cp : template.getContractedPowers()) {
            TariffContractedPower cloned = new TariffContractedPower();
            cloned.setPeriodCode(cp.getPeriodCode());
            cloned.setContractedPowerKw(cp.getContractedPowerKw());
            cloned.setTariff(clone);
            clonedPowers.add(cloned);
        }
        clone.setContractedPowers(clonedPowers);

        return clone;
    }

    /**
     * Aplica sobre un clon los campos no nulos de un TariffDto de overrides.
     * Solo reemplaza colecciones completas cuando el DTO las provee no vacías.
     */
    private void applyContractOverrides(Tariff tariff, TariffDto overrides) {
        if (overrides.name() != null) {
            tariff.setName(overrides.name());
        }
        if (overrides.market() != null) {
            tariff.setMarket(overrides.market());
        }
        if (overrides.accessTariffCode() != null) {
            tariff.setAccessTariffCode(overrides.accessTariffCode());
        }
        if (overrides.geographicZone() != null) {
            tariff.setGeographicZone(overrides.geographicZone());
        }
        if (overrides.energyCompany() != null) {
            tariff.setEnergyCompany(overrides.energyCompany());
        }

        if (overrides.periods() != null && !overrides.periods().isEmpty()) {
            List<Period> overriddenPeriods = new ArrayList<>();
            for (var dto : overrides.periods()) {
                Period p = new Period();
                p.setPeriodCode(dto.periodCode());
                p.setPriceKwh(dto.priceKwh());
                p.setTariff(tariff);
                overriddenPeriods.add(p);
            }
            tariff.setPeriods(overriddenPeriods);
        }

        if (overrides.contractedPowers() != null && !overrides.contractedPowers().isEmpty()) {
            List<TariffContractedPower> overriddenPowers = new ArrayList<>();
            for (var dto : overrides.contractedPowers()) {
                TariffContractedPower cp = new TariffContractedPower();
                cp.setPeriodCode(dto.periodCode());
                cp.setContractedPowerKw(dto.contractedPowerKw());
                cp.setTariff(tariff);
                overriddenPowers.add(cp);
            }
            tariff.setContractedPowers(overriddenPowers);
        }
    }

    private UserEntity findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado en sesión: " + username));
    }
}

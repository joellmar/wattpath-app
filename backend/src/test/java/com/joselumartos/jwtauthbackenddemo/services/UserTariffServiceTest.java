package com.joselumartos.jwtauthbackenddemo.services;

import com.joselumartos.jwtauthbackenddemo.dtos.PeriodDto;
import com.joselumartos.jwtauthbackenddemo.dtos.TariffContractedPowerDto;
import com.joselumartos.jwtauthbackenddemo.dtos.TariffDto;
import com.joselumartos.jwtauthbackenddemo.dtos.UserTariffRequest;
import com.joselumartos.jwtauthbackenddemo.entities.Period;
import com.joselumartos.jwtauthbackenddemo.entities.Role;
import com.joselumartos.jwtauthbackenddemo.entities.Tariff;
import com.joselumartos.jwtauthbackenddemo.entities.TariffContractedPower;
import com.joselumartos.jwtauthbackenddemo.entities.UserEntity;
import com.joselumartos.jwtauthbackenddemo.mappers.TariffDtoMapper;
import com.joselumartos.jwtauthbackenddemo.repositories.TariffRepository;
import com.joselumartos.jwtauthbackenddemo.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios de UserTariffService.
 *
 * Verifica el comportamiento multitenant sin levantar contexto Spring:
 *  - Aislamiento por Principal (no IDOR posible).
 *  - Clonado de plantilla sin mutar el original.
 *  - Asignación directa desde contrato.
 *  - Desvinculación correcta.
 *  - Propagación de errores de validación regulatoria.
 */
@ExtendWith(MockitoExtension.class)
class UserTariffServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TariffRepository tariffRepository;

    @Mock
    private TariffDtoMapper tariffDtoMapper;

    @Mock
    private TariffService tariffService;

    @InjectMocks
    private UserTariffService userTariffService;

    // -------------------------------------------------------------------------
    // getMyTariff
    // -------------------------------------------------------------------------

    @Test
    void getMyTariff_userWithoutTariff_returnsEmpty() {
        UserEntity user = buildUser("alice", null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        Optional<TariffDto> result = userTariffService.getMyTariff("alice");

        assertThat(result).isEmpty();
    }

    @Test
    void getMyTariff_userWithTariff_returnsMappedDto() {
        Tariff tariff = buildTariff20Td(10L);
        UserEntity user = buildUser("alice", tariff);
        TariffDto expectedDto = buildTariffDto(10L);

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(tariffDtoMapper.toDto(tariff)).thenReturn(expectedDto);

        Optional<TariffDto> result = userTariffService.getMyTariff("alice");

        assertThat(result).contains(expectedDto);
    }

    @Test
    void getMyTariff_unknownUser_throwsUsernameNotFoundException() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userTariffService.getMyTariff("ghost"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // saveMyTariff — clonar plantilla
    // -------------------------------------------------------------------------

    @Test
    void saveMyTariff_withTemplateId_savesNewEntityNotTemplate() {
        UserEntity user = buildUser("bob", null);
        Tariff template = buildTariff20Td(1L);
        Tariff savedClone = buildTariff20Td(99L);
        TariffDto expectedDto = buildTariffDto(99L);

        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));
        when(tariffRepository.findById(1L)).thenReturn(Optional.of(template));
        when(tariffRepository.save(any(Tariff.class))).thenReturn(savedClone);
        when(tariffDtoMapper.toDto(savedClone)).thenReturn(expectedDto);

        UserTariffRequest request = new UserTariffRequest(1L, null);
        TariffDto result = userTariffService.saveMyTariff("bob", request);

        assertThat(result).isEqualTo(expectedDto);

        // El clon guardado debe ser una entidad nueva (sin id previo)
        ArgumentCaptor<Tariff> captor = ArgumentCaptor.forClass(Tariff.class);
        verify(tariffRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isNull();
    }

    @Test
    void saveMyTariff_withTemplateId_doesNotMutateTemplate() {
        UserEntity user = buildUser("bob", null);
        Tariff template = buildTariff20Td(1L);
        String originalName = template.getName();
        Tariff savedClone = buildTariff20Td(99L);
        TariffDto expectedDto = buildTariffDto(99L);

        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));
        when(tariffRepository.findById(1L)).thenReturn(Optional.of(template));
        when(tariffRepository.save(any(Tariff.class))).thenReturn(savedClone);
        when(tariffDtoMapper.toDto(savedClone)).thenReturn(expectedDto);

        // Override con nombre diferente
        TariffDto contractOverride = new TariffDto(null, "Mi 2.0TD personalizada", null, null, null, null, null, null);
        UserTariffRequest request = new UserTariffRequest(1L, contractOverride);
        userTariffService.saveMyTariff("bob", request);

        // La plantilla original no debe haber cambiado de nombre
        assertThat(template.getName()).isEqualTo(originalName);
    }

    @Test
    void saveMyTariff_withTemplateId_linksCloneToUser() {
        UserEntity user = buildUser("bob", null);
        Tariff template = buildTariff20Td(1L);
        Tariff savedClone = buildTariff20Td(99L);
        TariffDto expectedDto = buildTariffDto(99L);

        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));
        when(tariffRepository.findById(1L)).thenReturn(Optional.of(template));
        when(tariffRepository.save(any(Tariff.class))).thenReturn(savedClone);
        when(tariffDtoMapper.toDto(savedClone)).thenReturn(expectedDto);
        when(userRepository.save(any(UserEntity.class))).thenReturn(user);

        userTariffService.saveMyTariff("bob", new UserTariffRequest(1L, null));

        // El usuario debe haberse guardado con el clon vinculado
        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getTariff()).isEqualTo(savedClone);
    }

    // -------------------------------------------------------------------------
    // saveMyTariff — contrato directo
    // -------------------------------------------------------------------------

    @Test
    void saveMyTariff_withContractOnly_mapsAndSavesDirectly() {
        UserEntity user = buildUser("carol", null);
        TariffDto contractDto = buildTariffDto(null);
        Tariff mappedEntity = buildTariff20Td(null);
        Tariff saved = buildTariff20Td(55L);
        TariffDto expectedDto = buildTariffDto(55L);

        when(userRepository.findByUsername("carol")).thenReturn(Optional.of(user));
        when(tariffDtoMapper.toEntity(contractDto)).thenReturn(mappedEntity);
        when(tariffRepository.save(mappedEntity)).thenReturn(saved);
        when(tariffDtoMapper.toDto(saved)).thenReturn(expectedDto);

        TariffDto result = userTariffService.saveMyTariff("carol", new UserTariffRequest(null, contractDto));

        assertThat(result).isEqualTo(expectedDto);
        verify(tariffRepository, never()).findById(any());
    }

    @Test
    void saveMyTariff_emptyRequest_throwsIllegalState() {
        UserTariffRequest emptyRequest = new UserTariffRequest(null, null);

        // No debe llegar a consultar el repositorio
        assertThatThrownBy(() -> userTariffService.saveMyTariff("bob", emptyRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("templateTariffId");

        verifyNoInteractions(userRepository, tariffRepository);
    }

    @Test
    void saveMyTariff_templateNotFound_throwsEntityNotFoundException() {
        UserEntity user = buildUser("bob", null);
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));
        when(tariffRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userTariffService.saveMyTariff("bob", new UserTariffRequest(999L, null)))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class)
                .hasMessageContaining("999");
    }

    // -------------------------------------------------------------------------
    // saveMyTariff — validación regulatoria
    // -------------------------------------------------------------------------

    @Test
    void saveMyTariff_invalidContract_propagatesValidationException() {
        UserEntity user = buildUser("carol", null);
        TariffDto badContract = new TariffDto(null, "Mala tarifa", null, "2.0TD", "PENINSULA", null,
                List.of(), List.of());
        Tariff mappedEntity = new Tariff();
        mappedEntity.setAccessTariffCode("2.0TD");
        mappedEntity.setGeographicZone("PENINSULA");
        mappedEntity.setPeriods(new ArrayList<>());
        mappedEntity.setContractedPowers(new ArrayList<>());

        when(userRepository.findByUsername("carol")).thenReturn(Optional.of(user));
        when(tariffDtoMapper.toEntity(badContract)).thenReturn(mappedEntity);
        doThrow(new IllegalStateException("El contrato debe incluir al menos un periodo de energía."))
                .when(tariffService).validateTariffContract(any(Tariff.class));

        assertThatThrownBy(() -> userTariffService.saveMyTariff("carol", new UserTariffRequest(null, badContract)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("periodo de energía");

        // No persiste si la validación falla
        verify(tariffRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // unlinkMyTariff
    // -------------------------------------------------------------------------

    @Test
    void unlinkMyTariff_setsTariffToNullAndDeletesClone() {
        Tariff existingTariff = buildTariff20Td(7L);
        UserEntity user = buildUser("dave", existingTariff);

        when(userRepository.findByUsername("dave")).thenReturn(Optional.of(user));
        when(userRepository.save(any(UserEntity.class))).thenReturn(user);

        userTariffService.unlinkMyTariff("dave");

        // El campo tariff del usuario debe quedar null
        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getTariff()).isNull();

        // El clon privado debe haberse borrado de la BD para no reaparecer en el catálogo
        verify(tariffRepository).deleteById(7L);
    }

    @Test
    void unlinkMyTariff_userWithoutTariff_doesNotSaveNorDelete() {
        UserEntity user = buildUser("dave", null);
        when(userRepository.findByUsername("dave")).thenReturn(Optional.of(user));

        assertThatNoException().isThrownBy(() -> userTariffService.unlinkMyTariff("dave"));

        // Sin tarifa asignada no hay nada que guardar ni borrar
        verify(userRepository, never()).save(any());
        verify(tariffRepository, never()).deleteById(any());
    }

    // -------------------------------------------------------------------------
    // IDOR: el servicio nunca acepta un userId externo
    // -------------------------------------------------------------------------

    @Test
    void getMyTariff_alwaysFetchesByAuthenticatedUsername() {
        // Simula que "alice" está autenticada: el servicio solo consulta por "alice"
        UserEntity alice = buildUser("alice", null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

        userTariffService.getMyTariff("alice");

        verify(userRepository).findByUsername("alice");
        // No existe forma de pasar un userId ajeno; el test no puede expresar
        // un ataque IDOR porque el servicio solo recibe username del Principal.
        verify(userRepository, never()).findById(any());
    }

    // -------------------------------------------------------------------------
    // Builders de datos de prueba
    // -------------------------------------------------------------------------

    private UserEntity buildUser(String username, Tariff tariff) {
        UserEntity user = new UserEntity(username, "hashed_pass", Role.ROLE_USER);
        user.setTariff(tariff);
        user.setEnabled(true);
        return user;
    }

    /**
     * Tarifa 2.0TD válida: energía P1-P3, potencia P1-P2.
     * El id se asigna manualmente para simular entidades ya persistidas.
     */
    private Tariff buildTariff20Td(Long id) {
        Tariff t = new Tariff();
        if (id != null) {
            t.setId(id);
        }
        t.setName("Tarifa 2.0TD Test");
        t.setAccessTariffCode("2.0TD");
        t.setGeographicZone("PENINSULA");

        List<Period> periods = new ArrayList<>();
        for (String code : new String[]{"P1", "P2", "P3"}) {
            Period p = new Period();
            p.setPeriodCode(code);
            p.setPriceKwh(new BigDecimal("0.120000"));
            p.setTariff(t);
            periods.add(p);
        }
        t.setPeriods(periods);

        List<TariffContractedPower> powers = new ArrayList<>();
        for (String code : new String[]{"P1", "P2"}) {
            TariffContractedPower cp = new TariffContractedPower();
            cp.setPeriodCode(code);
            cp.setContractedPowerKw(new BigDecimal("3.45"));
            cp.setTariff(t);
            powers.add(cp);
        }
        t.setContractedPowers(powers);

        return t;
    }

    private TariffDto buildTariffDto(Long id) {
        return new TariffDto(
                id,
                "Tarifa 2.0TD Test",
                "PVPC",
                "2.0TD",
                "PENINSULA",
                null,
                List.of(
                        new PeriodDto(null, "P1", new BigDecimal("0.120000")),
                        new PeriodDto(null, "P2", new BigDecimal("0.100000")),
                        new PeriodDto(null, "P3", new BigDecimal("0.080000"))
                ),
                List.of(
                        new TariffContractedPowerDto(null, "P1", new BigDecimal("3.45")),
                        new TariffContractedPowerDto(null, "P2", new BigDecimal("3.45"))
                )
        );
    }
}

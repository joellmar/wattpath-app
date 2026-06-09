package com.joselumartos.jwtauthbackenddemo.services;

import com.joselumartos.jwtauthbackenddemo.dtos.TariffDto;
import com.joselumartos.jwtauthbackenddemo.entities.Period;
import com.joselumartos.jwtauthbackenddemo.entities.Tariff;
import com.joselumartos.jwtauthbackenddemo.entities.TariffContractedPower;
import com.joselumartos.jwtauthbackenddemo.mappers.TariffDtoMapper;
import com.joselumartos.jwtauthbackenddemo.repositories.DeviceRepository;
import com.joselumartos.jwtauthbackenddemo.repositories.TariffRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios del validador de contratos TD en TariffService.
 *
 * Valida que las reglas de la Circular CNMC 3/2020 se aplican antes de persistir:
 *  - 2.0TD: energía P1-P3, potencia P1-P2
 *  - 3.0TD / 6.xTD: energía y potencia P1-P6, orden P1 <= P2 <= ... <= P6
 */
@ExtendWith(MockitoExtension.class)
class TariffServiceTest {

    @Mock
    private TariffRepository tariffRepository;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private TariffDtoMapper tariffDtoMapper;

    @Mock
    private CalendarResolverService calendarResolverService;

    @InjectMocks
    private TariffService tariffService;

    // -------------------------------------------------------------------------
    // 2.0TD — tarifa válida
    // -------------------------------------------------------------------------

    @Test
    void save_valid20TdTariff_doesNotThrow() {
        Tariff tariff = buildTariff("2.0TD", "PENINSULA",
                energyPeriods("P1", "P2", "P3"),
                powerPeriods(new String[]{"P1", "P2"}, new double[]{3.45, 3.45}));

        TariffDto dummyDto = new TariffDto(1L, "test", null, "2.0TD", "PENINSULA", null, null, null);
        when(tariffRepository.save(any())).thenReturn(tariff);
        when(tariffDtoMapper.toDto(any())).thenReturn(dummyDto);

        assertThatNoException().isThrownBy(() -> tariffService.save(tariff));
    }

    // -------------------------------------------------------------------------
    // 2.0TD — casos inválidos
    // -------------------------------------------------------------------------

    @Test
    void save_20TdMissingP3Energy_throwsIllegalState() {
        Tariff tariff = buildTariff("2.0TD", "PENINSULA",
                energyPeriods("P1", "P2"),                 // falta P3
                powerPeriods(new String[]{"P1", "P2"}, new double[]{3.45, 3.45}));

        assertThatThrownBy(() -> tariffService.save(tariff))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("P1, P2 y P3");
    }

    @Test
    void save_20TdMissingP2Power_throwsIllegalState() {
        Tariff tariff = buildTariff("2.0TD", "PENINSULA",
                energyPeriods("P1", "P2", "P3"),
                powerPeriods(new String[]{"P1"}, new double[]{3.45}));  // falta P2

        assertThatThrownBy(() -> tariffService.save(tariff))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("P1 y P2");
    }

    // -------------------------------------------------------------------------
    // 3.0TD — tarifa válida
    // -------------------------------------------------------------------------

    @Test
    void save_valid30TdTariff_doesNotThrow() {
        // Potencias ascendentes: P1 <= P2 <= ... <= P6
        Tariff tariff = buildTariff("3.0TD", "PENINSULA",
                energyPeriods("P1", "P2", "P3", "P4", "P5", "P6"),
                powerPeriods(
                        new String[]{"P1", "P2", "P3", "P4", "P5", "P6"},
                        new double[]{5.0, 7.0, 9.0, 11.0, 13.0, 15.0}));

        TariffDto dummyDto = new TariffDto(2L, "test", null, "3.0TD", "PENINSULA", null, null, null);
        when(tariffRepository.save(any())).thenReturn(tariff);
        when(tariffDtoMapper.toDto(any())).thenReturn(dummyDto);

        assertThatNoException().isThrownBy(() -> tariffService.save(tariff));
    }

    // -------------------------------------------------------------------------
    // 3.0TD — casos inválidos
    // -------------------------------------------------------------------------

    @Test
    void save_30TdMissingP4Energy_throwsIllegalState() {
        Tariff tariff = buildTariff("3.0TD", "PENINSULA",
                energyPeriods("P1", "P2", "P3", "P5", "P6"),    // falta P4
                powerPeriods(
                        new String[]{"P1", "P2", "P3", "P4", "P5", "P6"},
                        new double[]{5.0, 7.0, 9.0, 11.0, 13.0, 15.0}));

        assertThatThrownBy(() -> tariffService.save(tariff))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("P1, P2, P3, P4, P5 y P6");
    }

    @Test
    void save_30TdPowerOrderViolated_throwsIllegalState() {
        // P3 (10.0) > P4 (8.0) viola el orden legal P1<=P2<=P3<=P4<=P5<=P6
        Tariff tariff = buildTariff("3.0TD", "PENINSULA",
                energyPeriods("P1", "P2", "P3", "P4", "P5", "P6"),
                powerPeriods(
                        new String[]{"P1", "P2", "P3", "P4", "P5", "P6"},
                        new double[]{5.0, 7.0, 10.0, 8.0, 13.0, 15.0}));  // P3 > P4

        assertThatThrownBy(() -> tariffService.save(tariff))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("P3")
                .hasMessageContaining("P4");
    }

    @Test
    void save_30TdDuplicateEnergyPeriod_throwsIllegalState() {
        // P1 aparece dos veces en energía
        List<Period> periods = new ArrayList<>();
        periods.add(buildPeriod("P1", "0.120000"));
        periods.add(buildPeriod("P1", "0.100000"));  // duplicado
        periods.add(buildPeriod("P2", "0.090000"));
        periods.add(buildPeriod("P3", "0.080000"));
        periods.add(buildPeriod("P4", "0.070000"));
        periods.add(buildPeriod("P5", "0.060000"));
        periods.add(buildPeriod("P6", "0.050000"));

        Tariff tariff = buildTariff("3.0TD", "PENINSULA", periods,
                powerPeriods(
                        new String[]{"P1", "P2", "P3", "P4", "P5", "P6"},
                        new double[]{5.0, 7.0, 9.0, 11.0, 13.0, 15.0}));

        assertThatThrownBy(() -> tariffService.save(tariff))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicado")
                .hasMessageContaining("P1");
    }

    @Test
    void save_zeroPriceKwh_throwsIllegalState() {
        List<Period> periods = energyPeriods("P1", "P2", "P3");
        // P1 con precio cero
        periods.get(0).setPriceKwh(BigDecimal.ZERO);

        Tariff tariff = buildTariff("2.0TD", "PENINSULA", periods,
                powerPeriods(new String[]{"P1", "P2"}, new double[]{3.45, 3.45}));

        assertThatThrownBy(() -> tariffService.save(tariff))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mayor que cero");
    }

    @Test
    void save_nullAccessTariffCode_throwsIllegalState() {
        Tariff tariff = buildTariff(null, "PENINSULA",
                energyPeriods("P1", "P2", "P3"),
                powerPeriods(new String[]{"P1", "P2"}, new double[]{3.45, 3.45}));

        assertThatThrownBy(() -> tariffService.save(tariff))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("accessTariffCode");
    }

    @Test
    void save_61TdValidWithEqualPowers_doesNotThrow() {
        // P1 == P2 == ... == P6 es válido (la restricción es <=, no <)
        Tariff tariff = buildTariff("6.1TD", "PENINSULA",
                energyPeriods("P1", "P2", "P3", "P4", "P5", "P6"),
                powerPeriods(
                        new String[]{"P1", "P2", "P3", "P4", "P5", "P6"},
                        new double[]{10.0, 10.0, 10.0, 10.0, 10.0, 10.0}));

        TariffDto dummyDto = new TariffDto(3L, "test", null, "6.1TD", "PENINSULA", null, null, null);
        when(tariffRepository.save(any())).thenReturn(tariff);
        when(tariffDtoMapper.toDto(any())).thenReturn(dummyDto);

        assertThatNoException().isThrownBy(() -> tariffService.save(tariff));
    }

    // -------------------------------------------------------------------------
    // Builders de datos de prueba
    // -------------------------------------------------------------------------

    private Tariff buildTariff(String accessTariffCode, String geographicZone,
                                List<Period> periods, List<TariffContractedPower> contractedPowers) {
        Tariff tariff = new Tariff();
        tariff.setAccessTariffCode(accessTariffCode);
        tariff.setGeographicZone(geographicZone);
        tariff.setPeriods(periods);
        tariff.setContractedPowers(contractedPowers);
        return tariff;
    }

    /** Construye periodos de energía con precio 0.10 €/kWh para cada código dado. */
    private List<Period> energyPeriods(String... codes) {
        List<Period> result = new ArrayList<>();
        for (String code : codes) {
            result.add(buildPeriod(code, "0.100000"));
        }
        return result;
    }

    private Period buildPeriod(String periodCode, String price) {
        Period p = new Period();
        p.setPeriodCode(periodCode);
        p.setPriceKwh(new BigDecimal(price));
        return p;
    }

    /** Construye potencias contratadas con los kW indicados para cada código. */
    private List<TariffContractedPower> powerPeriods(String[] codes, double[] kws) {
        List<TariffContractedPower> result = new ArrayList<>();
        for (int i = 0; i < codes.length; i++) {
            TariffContractedPower cp = new TariffContractedPower();
            cp.setPeriodCode(codes[i]);
            cp.setContractedPowerKw(BigDecimal.valueOf(kws[i]));
            result.add(cp);
        }
        return result;
    }
}

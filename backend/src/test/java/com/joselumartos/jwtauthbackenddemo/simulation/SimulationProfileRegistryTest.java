package com.joselumartos.jwtauthbackenddemo.simulation;

import com.joselumartos.jwtauthbackenddemo.entities.Device;
import com.joselumartos.jwtauthbackenddemo.entities.SimulationProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationProfileRegistryTest {

    private SimulationProfileRegistry registry;
    private Device device;

    @BeforeEach
    void setUp() {
        registry = new SimulationProfileRegistry();
        device = new Device();
        device.setId(7L);
    }

    @ParameterizedTest
    @EnumSource(SimulationProfile.class)
    void everyProfileReturnsNonNegativeDeterministicPower(SimulationProfile profile) {
        Instant fixedInstant = Instant.parse("2026-06-24T12:00:00Z");

        BigDecimal first = registry.calculatePowerW(profile, fixedInstant, device);
        BigDecimal second = registry.calculatePowerW(profile, fixedInstant, device);

        assertThat(first).isNotNull();
        assertThat(first).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(first).isEqualByComparingTo(second);
    }

    @Test
    void sineWaveKeepsRegressionRangeAroundTwoHundredToEightHundredWatts() {
        Instant fixedInstant = Instant.parse("2026-06-24T12:00:00Z");

        BigDecimal powerW = registry.calculatePowerW(SimulationProfile.SINE_WAVE, fixedInstant, device);

        assertThat(powerW.doubleValue()).isBetween(200.0, 800.0);
    }

    @Test
    void nullProfileFallsBackToSineWave() {
        Instant fixedInstant = Instant.parse("2026-06-24T12:00:00Z");

        BigDecimal fallback = registry.calculatePowerW(null, fixedInstant, device);
        BigDecimal sineWave = registry.calculatePowerW(SimulationProfile.SINE_WAVE, fixedInstant, device);

        assertThat(fallback).isEqualByComparingTo(sineWave);
    }
}

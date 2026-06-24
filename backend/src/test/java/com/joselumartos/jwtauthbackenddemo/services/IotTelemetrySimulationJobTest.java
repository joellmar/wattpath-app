package com.joselumartos.jwtauthbackenddemo.services;

import com.joselumartos.jwtauthbackenddemo.config.SimulationProperties;
import com.joselumartos.jwtauthbackenddemo.entities.Device;
import com.joselumartos.jwtauthbackenddemo.entities.SimulationProfile;
import com.joselumartos.jwtauthbackenddemo.repositories.DeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IotTelemetrySimulationJobTest {

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private SimulationProperties simulationProperties;

    @Mock
    private SimulatedTelemetryProcessor telemetryProcessor;

    @InjectMocks
    private IotTelemetrySimulationJob simulationJob;

    private Device deviceA;
    private Device deviceB;

    @BeforeEach
    void setUp() {
        deviceA = new Device();
        deviceA.setId(1L);
        deviceA.setMacAddress("SIM000000001");
        deviceA.setIsOn(true);
        deviceA.setSimulated(true);
        deviceA.setSimulationProfile(SimulationProfile.SINE_WAVE);

        deviceB = new Device();
        deviceB.setId(2L);
        deviceB.setMacAddress("SIM000000002");
        deviceB.setIsOn(true);
        deviceB.setSimulated(true);
        deviceB.setSimulationProfile(SimulationProfile.TELEVISION);
    }

    @Test
    void calculateNextEnergyTotalDoesNotIncreaseWhenPowerIsZero() {
        BigDecimal previous = new BigDecimal("12.3456");

        BigDecimal next = simulationJob.calculateNextEnergyTotal(previous, BigDecimal.ZERO, 5000L);

        assertThat(next).isEqualByComparingTo(previous);
    }

    @Test
    void calculateNextEnergyTotalIncreasesMonotonicallyWithPositivePower() {
        BigDecimal previous = new BigDecimal("1.0000");

        BigDecimal next = simulationJob.calculateNextEnergyTotal(previous, new BigDecimal("500.00"), 5000L);

        assertThat(next).isGreaterThan(previous);
    }

    @Test
    void publishSimulatedTelemetryContinuesWhenOneDeviceFails() {
        when(simulationProperties.enabled()).thenReturn(true);
        when(simulationProperties.intervalMs()).thenReturn(5000L);
        when(deviceRepository.findBySimulatedTrue()).thenReturn(List.of(deviceA, deviceB));
        doThrow(new RuntimeException("duplicate key"))
                .when(telemetryProcessor)
                .processDevice(eq(deviceA), any(Instant.class), eq(5000L));

        simulationJob.publishSimulatedTelemetry();

        verify(telemetryProcessor).processDevice(eq(deviceA), any(Instant.class), eq(5000L));
        verify(telemetryProcessor).processDevice(eq(deviceB), any(Instant.class), eq(5000L));
    }

    @Test
    void publishSimulatedTelemetryDoesNothingWhenDisabled() {
        when(simulationProperties.enabled()).thenReturn(false);

        simulationJob.publishSimulatedTelemetry();

        verify(telemetryProcessor, never()).processDevice(any(), any(), any(Long.class));
    }
}

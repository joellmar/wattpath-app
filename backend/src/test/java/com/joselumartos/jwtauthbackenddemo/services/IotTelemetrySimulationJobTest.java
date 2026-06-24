package com.joselumartos.jwtauthbackenddemo.services;

import com.joselumartos.jwtauthbackenddemo.config.SimulationProperties;
import com.joselumartos.jwtauthbackenddemo.entities.Device;
import com.joselumartos.jwtauthbackenddemo.entities.Reading;
import com.joselumartos.jwtauthbackenddemo.entities.SimulationProfile;
import com.joselumartos.jwtauthbackenddemo.mappers.ReadingResponseMapper;
import com.joselumartos.jwtauthbackenddemo.repositories.DeviceRepository;
import com.joselumartos.jwtauthbackenddemo.repositories.ReadingRepository;
import com.joselumartos.jwtauthbackenddemo.simulation.SimulationProfileRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IotTelemetrySimulationJobTest {

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private ReadingRepository readingRepository;

    @Mock
    private ReadingService readingService;

    @Mock
    private TelemetryBroadcaster telemetryBroadcaster;

    @Mock
    private AlertService alertService;

    @Mock
    private ReadingResponseMapper readingResponseMapper;

    @Mock
    private SimulationProfileRegistry profileRegistry;

    @Mock
    private SimulationProperties simulationProperties;

    @InjectMocks
    private IotTelemetrySimulationJob simulationJob;

    private Device device;

    @BeforeEach
    void setUp() {
        device = new Device();
        device.setId(3L);
        device.setMacAddress("SIM000000001");
        device.setIsOn(false);
        device.setSimulated(true);
        device.setSimulationProfile(SimulationProfile.SINE_WAVE);
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
    void publishSimulatedTelemetryWritesZeroPowerWhenDeviceIsOff() {
        when(simulationProperties.enabled()).thenReturn(true);
        when(simulationProperties.intervalMs()).thenReturn(5000L);
        when(deviceRepository.findBySimulatedTrue()).thenReturn(List.of(device));
        when(readingRepository.findFirstByDeviceMacAddressOrderByTimeDesc("SIM000000001"))
                .thenReturn(Optional.of(readingWithKwh(new BigDecimal("4.0000"))));
        when(readingService.saveSimulatedReading(
                eq(device),
                any(Instant.class),
                eq(BigDecimal.ZERO),
                eq(new BigDecimal("4.0000")),
                eq(false)
        )).thenReturn(new Reading());

        simulationJob.publishSimulatedTelemetry();

        verify(profileRegistry, never()).calculatePowerW(any(), any(), any());
        verify(readingService).saveSimulatedReading(
                eq(device),
                any(Instant.class),
                eq(BigDecimal.ZERO),
                eq(new BigDecimal("4.0000")),
                eq(false)
        );
    }

    private Reading readingWithKwh(BigDecimal kwh) {
        Reading reading = new Reading();
        reading.setEnergyTotalKwh(kwh);
        return reading;
    }
}

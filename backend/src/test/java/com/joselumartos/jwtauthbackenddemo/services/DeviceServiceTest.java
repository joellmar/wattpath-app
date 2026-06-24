package com.joselumartos.jwtauthbackenddemo.services;

import com.joselumartos.jwtauthbackenddemo.dtos.CreateSimulatedDeviceRequest;
import com.joselumartos.jwtauthbackenddemo.dtos.DeviceDto;
import com.joselumartos.jwtauthbackenddemo.entities.Device;
import com.joselumartos.jwtauthbackenddemo.entities.SimulationProfile;
import com.joselumartos.jwtauthbackenddemo.entities.UserEntity;
import com.joselumartos.jwtauthbackenddemo.mappers.DeviceDtoMapper;
import com.joselumartos.jwtauthbackenddemo.repositories.DeviceRepository;
import com.joselumartos.jwtauthbackenddemo.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DeviceDtoMapper deviceDtoMapper;

    @InjectMocks
    private DeviceService deviceService;

    private UserEntity user;
    private Device simulatedDevice;

    @BeforeEach
    void setUp() {
        user = new UserEntity();
        user.setUsername("admin@wattimizer.dev");

        simulatedDevice = new Device();
        simulatedDevice.setId(10L);
        simulatedDevice.setName("Simulador horno");
        simulatedDevice.setMacAddress("SIM000000002");
        simulatedDevice.setUser(user);
        simulatedDevice.setIsOn(true);
        simulatedDevice.setSimulated(true);
        simulatedDevice.setSimulationProfile(SimulationProfile.OVEN);
    }

    @Test
    void createSimulatedDeviceAssignsOwnerProfileAndSyntheticMac() {
        CreateSimulatedDeviceRequest request = new CreateSimulatedDeviceRequest(
                "Simulador nevera",
                SimulationProfile.FRIDGE
        );

        when(userRepository.findByUsername("admin@wattimizer.dev")).thenReturn(Optional.of(user));
        when(deviceRepository.findByMacAddressStartingWith("SIM")).thenReturn(List.of(simulatedDevice));
        when(deviceRepository.findByMacAddress("SIM000000003")).thenReturn(Optional.empty());
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> {
            Device saved = invocation.getArgument(0);
            saved.setId(11L);
            return saved;
        });
        when(deviceDtoMapper.toDto(any(Device.class))).thenAnswer(invocation -> {
            Device saved = invocation.getArgument(0);
            return new DeviceDto(
                    saved.getId(),
                    user.getUsername(),
                    saved.getName(),
                    saved.getMacAddress(),
                    saved.getIsOn(),
                    saved.getSimulated(),
                    saved.getSimulationProfile()
            );
        });

        DeviceDto created = deviceService.createSimulatedDevice(request, "admin@wattimizer.dev");

        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).save(captor.capture());

        Device persisted = captor.getValue();
        assertThat(persisted.getSimulated()).isTrue();
        assertThat(persisted.getIsOn()).isTrue();
        assertThat(persisted.getSimulationProfile()).isEqualTo(SimulationProfile.FRIDGE);
        assertThat(persisted.getMacAddress()).startsWith("SIM");
        assertThat(created.simulationProfile()).isEqualTo(SimulationProfile.FRIDGE);
    }

    @Test
    void updateDeviceAllowsProfileChangeOnlyForSimulatedDevices() {
        DeviceDto updatePayload = new DeviceDto(
                10L,
                "otro@usuario.dev",
                "Nuevo nombre",
                "AA1122334455",
                false,
                false,
                SimulationProfile.TELEVISION
        );

        when(deviceRepository.findById(10L)).thenReturn(Optional.of(simulatedDevice));
        when(deviceRepository.save(simulatedDevice)).thenReturn(simulatedDevice);
        when(deviceDtoMapper.toDto(simulatedDevice)).thenReturn(updatePayload);

        deviceService.updateDevice(10L, updatePayload, "admin@wattimizer.dev");

        assertThat(simulatedDevice.getName()).isEqualTo("Nuevo nombre");
        assertThat(simulatedDevice.getIsOn()).isFalse();
        assertThat(simulatedDevice.getSimulationProfile()).isEqualTo(SimulationProfile.TELEVISION);
        assertThat(simulatedDevice.getMacAddress()).isEqualTo("SIM000000002");
        assertThat(simulatedDevice.getSimulated()).isTrue();
    }

    @Test
    void updateDeviceRejectsForeignOwner() {
        DeviceDto updatePayload = new DeviceDto(
                10L,
                "admin@wattimizer.dev",
                "Nuevo nombre",
                "SIM000000002",
                true,
                true,
                SimulationProfile.OVEN
        );

        when(deviceRepository.findById(10L)).thenReturn(Optional.of(simulatedDevice));

        assertThatThrownBy(() -> deviceService.updateDevice(10L, updatePayload, "otro@usuario.dev"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createDemoSimulatorPackSkipsProfilesAlreadyOwnedByUser() {
        when(userRepository.findByUsername("admin@wattimizer.dev")).thenReturn(Optional.of(user));
        when(deviceRepository.findByUserUsername("admin@wattimizer.dev"))
                .thenReturn(List.of(simulatedDevice));
        when(deviceRepository.findByMacAddressStartingWith("SIM")).thenReturn(List.of(simulatedDevice));
        when(deviceRepository.findByMacAddress(any())).thenReturn(Optional.empty());
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> {
            Device saved = invocation.getArgument(0);
            saved.setId(20L + saved.getSimulationProfile().ordinal());
            return saved;
        });
        when(deviceDtoMapper.toDto(any(Device.class))).thenAnswer(invocation -> {
            Device saved = invocation.getArgument(0);
            return new DeviceDto(
                    saved.getId(),
                    user.getUsername(),
                    saved.getName(),
                    saved.getMacAddress(),
                    saved.getIsOn(),
                    saved.getSimulated(),
                    saved.getSimulationProfile()
            );
        });

        List<DeviceDto> created = deviceService.createDemoSimulatorPack("admin@wattimizer.dev");

        assertThat(created).hasSize(SimulationProfile.values().length - 1);
        assertThat(created).noneMatch(device -> device.simulationProfile() == SimulationProfile.OVEN);
    }
}

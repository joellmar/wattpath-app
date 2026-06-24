package com.joselumartos.jwtauthbackenddemo.services;

import com.joselumartos.jwtauthbackenddemo.dtos.CreateSimulatedDeviceRequest;
import com.joselumartos.jwtauthbackenddemo.dtos.DeviceDto;
import com.joselumartos.jwtauthbackenddemo.entities.Device;
import com.joselumartos.jwtauthbackenddemo.entities.SimulationProfile;
import com.joselumartos.jwtauthbackenddemo.entities.UserEntity;
import com.joselumartos.jwtauthbackenddemo.mappers.DeviceDtoMapper;
import com.joselumartos.jwtauthbackenddemo.repositories.DeviceRepository;
import com.joselumartos.jwtauthbackenddemo.repositories.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DeviceService {
    private static final String SIMULATED_MAC_PREFIX = "SIM";

    private static final Map<SimulationProfile, String> DEMO_SIMULATOR_NAMES = demoSimulatorNames();

    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final DeviceDtoMapper deviceDtoMapper;

    @Transactional(readOnly = true)
    public List<DeviceDto> listAll() {
        return deviceDtoMapper.toDtoList(deviceRepository.findAll());
    }

    @Transactional(readOnly = true)
    public List<DeviceDto> listByUsername(String username) {
        return this.deviceRepository.findByUserUsername(username)
                .stream()
                .map(deviceDtoMapper::toDto)
                .toList();
    }

    @Transactional
    public DeviceDto save(DeviceDto dto) {
        Device device = deviceDtoMapper.toEntity(dto);
        Device saved = deviceRepository.save(device);
        return deviceDtoMapper.toDto(saved);
    }

    @Transactional
    public DeviceDto updateDevice(Long id, DeviceDto dto, String currentUsername) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Dispositivo no encontrado"));

        if (device.getUser() == null || !device.getUser().getUsername().equals(currentUsername)) {
            throw new IllegalStateException("No tienes permisos para modificar este equipo.");
        }

        device.setName(dto.name());
        device.setIsOn(dto.isOn());

        if (Boolean.TRUE.equals(device.getSimulated()) && dto.simulationProfile() != null) {
            device.setSimulationProfile(dto.simulationProfile());
        }

        return deviceDtoMapper.toDto(deviceRepository.save(device));
    }

    @Transactional
    public DeviceDto createSimulatedDevice(CreateSimulatedDeviceRequest request, String currentUsername) {
        if (request.simulationProfile() == null) {
            throw new IllegalArgumentException("Debes seleccionar un perfil de simulación.");
        }
        if (request.name() == null || request.name().trim().length() < 3) {
            throw new IllegalArgumentException("El nombre del dispositivo es obligatorio (mínimo 3 caracteres).");
        }

        UserEntity currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no localizado en sesión: " + currentUsername));

        Device device = new Device();
        device.setName(request.name().trim());
        device.setMacAddress(generateUniqueSimulatedMacAddress());
        device.setUser(currentUser);
        device.setIsOn(true);
        device.setSimulated(true);
        device.setSimulationProfile(request.simulationProfile());

        return deviceDtoMapper.toDto(deviceRepository.save(device));
    }

    @Transactional
    public List<DeviceDto> createDemoSimulatorPack(String currentUsername) {
        List<DeviceDto> createdDevices = new ArrayList<>();

        for (SimulationProfile profile : SimulationProfile.values()) {
            if (userAlreadyHasSimulatedProfile(currentUsername, profile)) {
                continue;
            }

            CreateSimulatedDeviceRequest request = new CreateSimulatedDeviceRequest(
                    DEMO_SIMULATOR_NAMES.get(profile),
                    profile
            );
            createdDevices.add(createSimulatedDevice(request, currentUsername));
        }

        return createdDevices;
    }

    @Transactional(readOnly = true)
    public DeviceDto findById(Long id) {
        return deviceRepository
                .findById(id)
                .map(deviceDtoMapper::toDto)
                .orElseThrow(() -> new EntityNotFoundException("Device not found"));
    }

    @Transactional(readOnly = true)
    public DeviceDto findByMacAddress(String macAddress) {
        return deviceRepository
                .findByMacAddress(macAddress)
                .map(deviceDtoMapper::toDto)
                .orElseThrow(() -> new EntityNotFoundException("Device not found with MAC: " + macAddress));
    }

    @Transactional
    public DeviceDto claimOrRegisterDevice(String macAddress, String newName, String currentUsername) {
        UserEntity currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no localizado en sesión: " + currentUsername));

        Device device = deviceRepository.findByMacAddress(macAddress).orElse(null);
        if (device == null) {
            device = new Device();
            device.setMacAddress(macAddress);
            device.setName(newName);
            device.setUser(currentUser);
            device.setIsOn(true);
            device.setSimulated(false);
        } else {
            if (device.getUser() != null && !device.getUser().getUsername().equals("SYSTEM") && !device.getUser().getUsername().equals(currentUsername)) {
                throw new IllegalStateException("Este dispositivo ya se encuentra vinculado a otra cuenta empresarial.");
            }

            device.setUser(currentUser);
            if (newName != null && !newName.trim().isEmpty()) {
                device.setName(newName);
            }
        }

        Device updatedDevice = deviceRepository.save(device);
        return deviceDtoMapper.toDto(updatedDevice);
    }

    @Transactional
    public void deleteById(Long id) {
        if (!deviceRepository.existsById(id)) {
            throw new EntityNotFoundException("Device not found with id: " + id);
        }
        deviceRepository.deleteById(id);
    }

    @Transactional
    public void delete(Device device) {
        deviceRepository.delete(device);
    }

    private String generateUniqueSimulatedMacAddress() {
        int nextSequence = deviceRepository.findByMacAddressStartingWith(SIMULATED_MAC_PREFIX).stream()
                .map(Device::getMacAddress)
                .map(this::extractSimulatedSequence)
                .max(Comparator.naturalOrder())
                .orElse(0) + 1;

        for (int attempt = 0; attempt < 100; attempt++) {
            String candidate = formatSimulatedMac(nextSequence + attempt);
            if (deviceRepository.findByMacAddress(candidate).isEmpty()) {
                return candidate;
            }
        }

        throw new IllegalStateException("No se pudo generar una dirección MAC simulada única.");
    }

    private int extractSimulatedSequence(String macAddress) {
        if (macAddress == null || !macAddress.startsWith(SIMULATED_MAC_PREFIX)) {
            return 0;
        }
        String suffix = macAddress.substring(SIMULATED_MAC_PREFIX.length());
        try {
            return Integer.parseInt(suffix);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String formatSimulatedMac(int sequence) {
        return SIMULATED_MAC_PREFIX + String.format("%09d", sequence);
    }

    private boolean userAlreadyHasSimulatedProfile(String username, SimulationProfile profile) {
        return deviceRepository.findByUserUsername(username).stream()
                .anyMatch(device -> Boolean.TRUE.equals(device.getSimulated())
                        && profile.equals(device.getSimulationProfile()));
    }

    private static Map<SimulationProfile, String> demoSimulatorNames() {
        Map<SimulationProfile, String> names = new EnumMap<>(SimulationProfile.class);
        names.put(SimulationProfile.SINE_WAVE, "Simulador onda de prueba");
        names.put(SimulationProfile.OVEN, "Simulador horno");
        names.put(SimulationProfile.WASHING_MACHINE, "Simulador lavadora");
        names.put(SimulationProfile.TELEVISION, "Simulador televisor");
        names.put(SimulationProfile.FAN, "Simulador ventilador");
        names.put(SimulationProfile.DESKTOP_PC, "Simulador PC");
        names.put(SimulationProfile.FRIDGE, "Simulador nevera");
        names.put(SimulationProfile.STANDBY, "Simulador consumo fantasma");
        names.put(SimulationProfile.CONSTANT_HIGH_LOAD, "Simulador carga alta");
        return Map.copyOf(names);
    }
}

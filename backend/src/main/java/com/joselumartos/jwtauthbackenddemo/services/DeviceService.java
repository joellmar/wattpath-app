package com.joselumartos.jwtauthbackenddemo.services;

import com.joselumartos.jwtauthbackenddemo.dtos.DeviceDto;
import com.joselumartos.jwtauthbackenddemo.entities.Device;
import com.joselumartos.jwtauthbackenddemo.entities.UserEntity;
import com.joselumartos.jwtauthbackenddemo.mappers.DeviceDtoMapper;
import com.joselumartos.jwtauthbackenddemo.repositories.DeviceRepository;
import com.joselumartos.jwtauthbackenddemo.repositories.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeviceService {
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

        return deviceDtoMapper.toDto(deviceRepository.save(device));
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
}

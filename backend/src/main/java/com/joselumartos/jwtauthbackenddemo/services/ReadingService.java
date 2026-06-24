package com.joselumartos.jwtauthbackenddemo.services;

import com.joselumartos.jwtauthbackenddemo.dtos.*;
import com.joselumartos.jwtauthbackenddemo.entities.Device;
import com.joselumartos.jwtauthbackenddemo.entities.Reading;
import com.joselumartos.jwtauthbackenddemo.mappers.DeviceDtoMapper;
import com.joselumartos.jwtauthbackenddemo.mappers.EventsRpcMapper;
import com.joselumartos.jwtauthbackenddemo.mappers.ReadingResponseMapper;
import com.joselumartos.jwtauthbackenddemo.mappers.StatusMapper;
import com.joselumartos.jwtauthbackenddemo.repositories.DeviceRepository;
import com.joselumartos.jwtauthbackenddemo.repositories.ReadingRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReadingService {
    private final ReadingRepository readingRepository;
    private final DeviceRepository deviceRepository;
    private final EventsRpcMapper eventsRpcMapper;
    private final StatusMapper statusMapper;
    private final ReadingResponseMapper readingResponseMapper;
    private final DeviceDtoMapper deviceDtoMapper;

    public List<ReadingResponse> listAll() {
        return this.readingRepository
                .findAll()
                .stream()
                .map(readingResponseMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReadingResponse> listByUsername(String username) {
        return readingRepository.findAll()
                .stream()
                .filter(reading -> reading.getDevice() != null && reading.getDevice().getUser() != null && reading.getDevice().getUser().getUsername().equals(username))
                .map(readingResponseMapper::toDto)
                .toList();
    }

    @Transactional
    public ReadingResponse save(EventsRpc dto) {
        Reading saved = saveEntity(dto);
        return readingResponseMapper.toDto(saved);
    }

    @Transactional
    public ReadingResponse save(DeviceDto deviceDto, Status dto) {
        Reading saved = saveEntity(deviceDto, dto);
        return readingResponseMapper.toDto(saved);
    }

    @Transactional
    public Reading saveEntity(EventsRpc dto) {
        Reading reading = eventsRpcMapper.toEntity(dto);

        String macAddress = (reading.getDevice() == null || reading.getDevice().getMacAddress() == null)
                ? ""
                : reading.getDevice().getMacAddress();

        Device managedDevice = deviceRepository.findByMacAddress(macAddress)
                .orElseGet(() -> {
                    Device newDevice = new Device();
                    newDevice.setMacAddress(macAddress);
                    newDevice.setName("Nuevo Enchufe " + macAddress);
                    newDevice.setIsOn(true);
                    return deviceRepository.save(newDevice);
                });

        reading.setDevice(managedDevice);
        return readingRepository.save(reading);
    }

    @Transactional
    public Reading saveEntity(DeviceDto deviceDto, Status dto) {
        Reading reading = statusMapper.toEntity(dto);
        reading.setTime(Instant.now());
        reading.setDevice(deviceDtoMapper.toEntity(deviceDto));

        return readingRepository.save(reading);
    }

    @Transactional(readOnly = true)
    public ReadingResponse findByTimeAndMacAddress(Instant time, String macAddress) {
        return readingRepository
                .findByTimeAndDeviceMacAddress(time, macAddress)
                .map(readingResponseMapper::toDto)
                .orElseThrow(() -> new EntityNotFoundException("Reading not found with time: " + time + " and MAC address: " + macAddress));
    }

    @Transactional(readOnly = true)
    public ReadingResponse findByDevice(String macAddress) {
        return readingRepository
                .findFirstByDeviceMacAddressOrderByTimeDesc(macAddress)
                .map(readingResponseMapper::toDto)
                .orElseThrow(() -> new EntityNotFoundException("Reading not found with MAC address: " + macAddress));
    }

    @Transactional(readOnly = true)
    public List<ReadingResponse> listRecentByMacAddress(String macAddress, int seconds) {
        Instant end = Instant.now();
        Instant start = end.minus(Math.max(seconds, 1), ChronoUnit.SECONDS);
        return readingRepository.findReadingsInInterval(macAddress, start, end)
                .stream()
                .map(readingResponseMapper::toDto)
                .toList();
    }

    @Transactional
    public void deleteByTimeAndMacAddress(Instant time, String macAddress) {
        readingRepository.findByTimeAndDeviceMacAddress(time, macAddress)
                .orElseThrow(() -> new EntityNotFoundException("Reading not found with time: " + time + " and MAC address: " + macAddress));

        readingRepository.deleteByTimeAndDeviceMacAddress(time, macAddress);
    }

    @Transactional
    public void delete(Reading reading) {
        if (reading == null) {
            throw new EntityNotFoundException("Reading not found");
        }

        readingRepository.delete(reading);
    }

    // Construye y persiste una lectura desde el job de simulación sin pasar por MapStruct,
    // ya que los datos ya están calculados y no vienen de un DTO de MQTT externo.
    @Transactional
    public Reading saveSimulatedReading(
            Device device,
            Instant time,
            BigDecimal powerW,
            BigDecimal energyTotalKwh,
            Boolean isOn
    ) {
        Reading reading = new Reading();
        reading.setTime(time);
        reading.setDevice(device);
        reading.setPowerW(powerW);
        reading.setEnergyTotalKwh(energyTotalKwh);
        reading.setIsOn(isOn);

        return readingRepository.save(reading);
    }
}

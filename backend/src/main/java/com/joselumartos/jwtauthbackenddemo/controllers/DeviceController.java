package com.joselumartos.jwtauthbackenddemo.controllers;

import com.joselumartos.jwtauthbackenddemo.dtos.CreateSimulatedDeviceRequest;
import com.joselumartos.jwtauthbackenddemo.dtos.DeviceDto;
import com.joselumartos.jwtauthbackenddemo.services.DeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class DeviceController {
    private final DeviceService deviceService;

    @GetMapping
    public ResponseEntity<List<DeviceDto>> listDevices(Principal principal) {
        List<DeviceDto> deviceDtos = deviceService.listByUsername(principal.getName());
        return ResponseEntity.ok(deviceDtos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeviceDto> getDeviceById(@PathVariable Long id, Principal principal) {
        DeviceDto device = deviceService.findById(id);

        if (!device.username().equals(principal.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(device);
    }

    @PostMapping
    public ResponseEntity<DeviceDto> createDevice(@RequestBody DeviceDto deviceDto) {
        return new ResponseEntity<>(deviceService.save(deviceDto), HttpStatus.CREATED);
    }

    @PostMapping("/claim")
    public ResponseEntity<DeviceDto> claimDevice(@RequestBody DeviceDto deviceDto, Principal principal) {
        DeviceDto claimed = deviceService.claimOrRegisterDevice(deviceDto.macAddress(), deviceDto.name(), principal.getName());
        return ResponseEntity.ok(claimed);
    }

    @PostMapping("/simulated")
    public ResponseEntity<DeviceDto> createSimulatedDevice(
            @RequestBody CreateSimulatedDeviceRequest request,
            Principal principal
    ) {
        DeviceDto created = deviceService.createSimulatedDevice(request, principal.getName());
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DeviceDto> updateDevice(@PathVariable Long id, @RequestBody DeviceDto deviceDto, Principal principal) {
        return ResponseEntity.ok(deviceService.updateDevice(id, deviceDto, principal.getName()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDevice(@PathVariable Long id, Principal principal) {
        DeviceDto existing = deviceService.findById(id);
        if (!existing.username().equals(principal.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        deviceService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}

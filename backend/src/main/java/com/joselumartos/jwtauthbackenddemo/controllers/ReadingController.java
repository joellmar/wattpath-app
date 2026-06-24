package com.joselumartos.jwtauthbackenddemo.controllers;

import com.joselumartos.jwtauthbackenddemo.dtos.DeviceDto;
import com.joselumartos.jwtauthbackenddemo.dtos.ReadingResponse;
import com.joselumartos.jwtauthbackenddemo.entities.Reading;
import com.joselumartos.jwtauthbackenddemo.services.DeviceService;
import com.joselumartos.jwtauthbackenddemo.services.ReadingService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Instant;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/readings")
public class ReadingController {

private final ReadingService readingService;
private final DeviceService deviceService;

    @GetMapping
    public ResponseEntity<List<ReadingResponse>> getAllReadings(Principal principal) {
        List<ReadingResponse> readings = readingService.listByUsername(principal.getName());
        return ResponseEntity.ok(readings);
    }

    @GetMapping("/latest/{macAddress}")
    public ResponseEntity<ReadingResponse> getLatestReadingByDevice(@PathVariable String macAddress, Principal principal) {
        DeviceDto device = deviceService.findByMacAddress(macAddress);
        if (!device.username().equals(principal.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        ReadingResponse latest = readingService.findByDevice(macAddress);
        return ResponseEntity.ok(latest);
    }

    @GetMapping("/device/{macAddress}/recent")
    public ResponseEntity<List<ReadingResponse>> getRecentReadingsByDevice(
            @PathVariable String macAddress,
            @RequestParam(defaultValue = "120") int seconds,
            Principal principal
    ) {
        DeviceDto device = deviceService.findByMacAddress(macAddress);
        if (!device.username().equals(principal.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<ReadingResponse> readings = readingService.listRecentByMacAddress(macAddress, seconds);
        return ResponseEntity.ok(readings);
    }

    @GetMapping("/search")
    public ResponseEntity<ReadingResponse> getReadingByCompositeKey(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant time,
            @RequestParam String macAddress,
            Principal principal
    ) {
        DeviceDto device = deviceService.findByMacAddress(macAddress);
        if (!device.username().equals(principal.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        ReadingResponse reading = readingService.findByTimeAndMacAddress(time, macAddress);
        return ResponseEntity.ok(reading);
    }

    @DeleteMapping("/search")
    public ResponseEntity<Void> deleteReadingByCompositeKey(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant time,
            @RequestParam String macAddress,
            Principal principal
    ) {
        DeviceDto device = deviceService.findByMacAddress(macAddress);
        if (!device.username().equals(principal.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        readingService.deleteByTimeAndMacAddress(time, macAddress);
        return ResponseEntity.noContent().build();
    }
}

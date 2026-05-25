package com.joselumartos.jwtauthbackenddemo.controllers;

import com.joselumartos.jwtauthbackenddemo.dtos.DeviceDto;
import com.joselumartos.jwtauthbackenddemo.services.ConsumptionService;
import com.joselumartos.jwtauthbackenddemo.services.DeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/analytics")
public class ConsumptionController {

    private final ConsumptionService consumptionService;
    private final DeviceService deviceService;

    @GetMapping("/cost")
    public ResponseEntity<Map<String, Object>> getEnergyCost(
            @RequestParam String macAddress,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)Instant start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)Instant end,
            Principal principal) {

        DeviceDto device = deviceService.findByMacAddress(macAddress);
        if (!device.username().equals(principal.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        BigDecimal totalEur = consumptionService.calculateCostInPeriod(macAddress, start, end);

        Map<String, Object> response = new HashMap<>();
        response.put("macAddress", macAddress);
        response.put("totalCostEur", totalEur);
        response.put("start", start);
        response.put("end", end);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/ghost-consumption")
    public ResponseEntity<Map<String, Object>> getGhostConsumption(
            @RequestParam String macAddress,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)Instant start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)Instant end,
            Principal principal) {

        DeviceDto device = deviceService.findByMacAddress(macAddress);
        if (!device.username().equals(principal.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        BigDecimal ghostEur = consumptionService.calculateGhostCost(macAddress, start, end);

        Map<String, Object> response = new HashMap<>();
        response.put("macAddress", macAddress);
        response.put("ghostCostEur", ghostEur);
        response.put("start", start);
        response.put("end", end);

        return ResponseEntity.ok(response);
    }
}

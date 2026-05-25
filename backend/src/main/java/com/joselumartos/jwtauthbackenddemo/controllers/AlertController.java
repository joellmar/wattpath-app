package com.joselumartos.jwtauthbackenddemo.controllers;

import com.joselumartos.jwtauthbackenddemo.dtos.AlertDto;
import com.joselumartos.jwtauthbackenddemo.dtos.DeviceDto;
import com.joselumartos.jwtauthbackenddemo.services.AlertService;
import com.joselumartos.jwtauthbackenddemo.services.DeviceService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertService alertService;
    private final DeviceService deviceService;

    @GetMapping
    public ResponseEntity<List<AlertDto>> getAllAlerts(Principal principal) {
        List<AlertDto> alerts = alertService.listByUsername(principal.getName());
        return ResponseEntity.ok(alerts);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> clearAlert(@PathVariable Long id, Principal principal) {
        int deletedRows = alertService.deleteAlertForUser(id, principal.getName());

        if (deletedRows == 0) {
            throw new EntityNotFoundException("Alert not found or unauthorized to delete");
        }
        return ResponseEntity.noContent().build();
    }
}

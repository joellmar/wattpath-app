package com.joselumartos.jwtauthbackenddemo.controllers;

import com.joselumartos.jwtauthbackenddemo.dtos.TariffDto;
import com.joselumartos.jwtauthbackenddemo.services.TariffService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tariffs")
public class TariffController {
    private final TariffService tariffService;

    @GetMapping
    public ResponseEntity<List<TariffDto>> getAllTariffs() {
        return ResponseEntity.ok(tariffService.listAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TariffDto> getTariffById(@PathVariable Long id) {
        return ResponseEntity.ok(tariffService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TariffDto> createTariff(@RequestBody TariffDto tariffDto) {
        return new ResponseEntity<>(tariffService.save(tariffDto), HttpStatus.CREATED);
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TariffDto> updateTariff(@PathVariable Long id, @RequestBody TariffDto tariffDto) {
        TariffDto updatedDto = new TariffDto(id, tariffDto.name(), tariffDto.type(), tariffDto.market(), tariffDto.contractedPowerKw(), tariffDto.energyCompany(), tariffDto.periods());
        return ResponseEntity.ok(tariffService.save(updatedDto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteTariff(@PathVariable Long id) {
        tariffService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}

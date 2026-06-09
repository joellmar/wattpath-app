package com.joselumartos.jwtauthbackenddemo.controllers;

import com.joselumartos.jwtauthbackenddemo.dtos.TariffDto;
import com.joselumartos.jwtauthbackenddemo.dtos.UserTariffRequest;
import com.joselumartos.jwtauthbackenddemo.services.UserTariffService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Optional;

/**
 * API multitenant de tarifa privada del usuario autenticado.
 *
 * El propietario del recurso se extrae exclusivamente del token JWT (Principal).
 * No se aceptan user IDs en path ni body para evitar IDOR.
 *
 * GET    /api/v1/users/me/tariff  -> 200 TariffDto | 204 No Content
 * POST   /api/v1/users/me/tariff  -> 200 TariffDto
 * DELETE /api/v1/users/me/tariff  -> 204 No Content
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/me/tariff")
public class UserTariffController {

    private final UserTariffService userTariffService;

    @GetMapping
    public ResponseEntity<TariffDto> getMyTariff(Principal principal) {
        Optional<TariffDto> tariff = userTariffService.getMyTariff(principal.getName());
        return tariff.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping
    public ResponseEntity<TariffDto> saveMyTariff(Principal principal,
                                                   @RequestBody UserTariffRequest request) {
        TariffDto saved = userTariffService.saveMyTariff(principal.getName(), request);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping
    public ResponseEntity<Void> unlinkMyTariff(Principal principal) {
        userTariffService.unlinkMyTariff(principal.getName());
        return ResponseEntity.noContent().build();
    }
}

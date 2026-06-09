package com.joselumartos.jwtauthbackenddemo.dtos;

/**
 * Payload de entrada para asignar o actualizar la tarifa privada del usuario autenticado.
 *
 * Dos modos de uso mutuamente complementarios:
 *  - Solo templateTariffId: el servicio clona la plantilla del catálogo maestro tal cual.
 *  - templateTariffId + contract: clona la plantilla y aplica los campos de contract como overrides.
 *  - Solo contract: crea/actualiza el contrato privado desde los datos enviados directamente.
 *
 * No se acepta userId: el propietario se resuelve siempre desde Principal (token JWT).
 */
public record UserTariffRequest(
        Long templateTariffId,
        TariffDto contract
) {}

package com.joselumartos.jwtauthbackenddemo.controllers;

/**
 * Excepción de dominio que el GlobalExceptionHandler traduce a 403 Forbidden.
 * Se usa para el acceso denegado a rutas de administración protegidas por secreto.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}

package com.joselumartos.jwtauthbackenddemo.entities;

/**
 * Proveedores de identidad federada soportados.
 * El nombre del enum debe coincidir (en mayúsculas) con el registrationId
 * configurado en spring.security.oauth2.client.registration.{provider}.
 */
public enum OAuthProvider {
    GOOGLE,
    GITHUB
}

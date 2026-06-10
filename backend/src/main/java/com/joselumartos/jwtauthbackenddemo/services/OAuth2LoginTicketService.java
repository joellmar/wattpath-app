package com.joselumartos.jwtauthbackenddemo.services;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Almacén temporal de tickets de un solo uso para el intercambio OAuth2.
 *
 * Flujo: el SuccessHandler genera un ticket aleatorio que mapea al JWT corporativo.
 * Angular lo canjea inmediatamente en /api/v1/auth/oauth/exchange.
 * El ticket se invalida en el primer uso y expira a los 60 segundos.
 *
 * Para una sola instancia (MVP), ConcurrentHashMap es suficiente.
 * Si se escala a múltiples instancias, migrar a Redis con TTL.
 */
@Service
public class OAuth2LoginTicketService {

    private static final int TICKET_TTL_SECONDS = 60;

    private record TicketEntry(String jwt, Instant createdAt) {}

    private final ConcurrentHashMap<String, TicketEntry> store = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    public String createTicket(String jwt) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        store.put(ticket, new TicketEntry(jwt, Instant.now()));
        return ticket;
    }

    /**
     * Consume el ticket y devuelve el JWT asociado.
     * Lanza {@link IllegalStateException} si el ticket es inválido, ya fue consumido o expiró.
     */
    public String consumeTicket(String ticket) {
        TicketEntry entry = store.remove(ticket);

        if (entry == null) {
            throw new IllegalStateException("El ticket de acceso no es válido o ya fue utilizado.");
        }
        if (entry.createdAt().plusSeconds(TICKET_TTL_SECONDS).isBefore(Instant.now())) {
            throw new IllegalStateException("El ticket de acceso ha expirado. Inicia sesión de nuevo.");
        }

        return entry.jwt();
    }
}

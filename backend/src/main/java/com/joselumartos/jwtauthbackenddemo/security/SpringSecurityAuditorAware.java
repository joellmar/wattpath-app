package com.joselumartos.jwtauthbackenddemo.security;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public class SpringSecurityAuditorAware implements AuditorAware<String> {
    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Comprobar si la autenticación es nula, si no está autenticado o si es el anonymousUser
        // Spring usa "anonynomousUser" para todas aquellas peticiones no autenticadas
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal().equals("anonymousUser")) {
            return Optional.of("SYSTEM"); // Usuario por defecto si no hay login
        }

        return Optional.ofNullable(authentication.getName()); // Devuelve el username
    }
}

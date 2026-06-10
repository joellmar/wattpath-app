package com.joselumartos.jwtauthbackenddemo.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reemplaza el repositorio por defecto basado en sesión HTTP por una solución
 * stateless: el state OAuth2 viaja en una cookie HttpOnly de corta duración,
 * mientras el objeto completo de la petición vive en memoria (ConcurrentHashMap).
 *
 * Esto preserva SessionCreationPolicy.STATELESS para el resto de la API.
 * El state ya es un valor aleatorio generado por Spring Security, por lo que
 * usarlo como clave del mapa es seguro.
 */
@Component
public class CookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final String COOKIE_NAME = "OAUTH2_STATE";
    private static final int COOKIE_MAX_AGE_SECONDS = 180;

    private record AuthRequestEntry(OAuth2AuthorizationRequest request, Instant createdAt) {}

    private final ConcurrentHashMap<String, AuthRequestEntry> requestStore = new ConcurrentHashMap<>();

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return getStateFromCookie(request)
                .map(state -> {
                    AuthRequestEntry entry = requestStore.get(state);
                    if (entry == null) return null;
                    if (isExpired(entry)) {
                        requestStore.remove(state);
                        return null;
                    }
                    return entry.request();
                })
                .orElse(null);
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request,
            HttpServletResponse response) {

        if (authorizationRequest == null) {
            removeAuthorizationRequest(request, response);
            return;
        }

        String state = authorizationRequest.getState();
        requestStore.put(state, new AuthRequestEntry(authorizationRequest, Instant.now()));

        Cookie cookie = new Cookie(COOKIE_NAME, state);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(COOKIE_MAX_AGE_SECONDS);
        response.addCookie(cookie);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request,
            HttpServletResponse response) {

        return getStateFromCookie(request)
                .map(state -> {
                    AuthRequestEntry entry = requestStore.remove(state);
                    deleteCookie(response);
                    if (entry == null || isExpired(entry)) return null;
                    return entry.request();
                })
                .orElse(null);
    }

    private Optional<String> getStateFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return Optional.empty();
        return Arrays.stream(cookies)
                .filter(c -> COOKIE_NAME.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }

    private void deleteCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(COOKIE_NAME, "");
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    private boolean isExpired(AuthRequestEntry entry) {
        return entry.createdAt().plusSeconds(COOKIE_MAX_AGE_SECONDS).isBefore(Instant.now());
    }
}

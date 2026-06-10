package com.joselumartos.jwtauthbackenddemo.services;

import com.joselumartos.jwtauthbackenddemo.security.StoreProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;

/**
 * Fuente única de generación del JWT corporativo.
 * Lo usan tanto el login clásico como el flujo OAuth2 social,
 * garantizando que ambos flujos emitan tokens con los mismos claims.
 */
@Service
public class JwtTokenService {

    private static final long EXPIRATION_MS = 8L * 60 * 60 * 1000;

    private final StoreProperties storeProperties;

    public JwtTokenService(StoreProperties storeProperties) {
        this.storeProperties = storeProperties;
    }

    /**
     * Genera un JWT firmado con HMAC-SHA.
     * Claims obligatorios para la SPA Angular: {@code username} y {@code authorities}.
     */
    public String generateJwt(String username, Collection<? extends GrantedAuthority> authorities) {
        SecretKey secretKey = Keys.hmacShaKeyFor(
                storeProperties.jwtSecretKeyValue().getBytes(StandardCharsets.UTF_8)
        );

        return Jwts.builder()
                .issuer("store-security")
                .subject("JWT Token")
                .claim("username", username)
                .claim("authorities", authorities.stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.joining(",")))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(secretKey)
                .compact();
    }
}

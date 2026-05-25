package com.joselumartos.jwtauthbackenddemo.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StoreProperties {

    @Value("${jwt.secret}")
    private String jwtSecret;

    public String jwtSecretKeyValue() {
        return this.jwtSecret;
    }
}

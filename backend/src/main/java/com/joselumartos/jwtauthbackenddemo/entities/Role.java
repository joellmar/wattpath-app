package com.joselumartos.jwtauthbackenddemo.entities;

public enum Role {
    ROLE_USER,
    ROLE_ADMIN;

    public String getSimpleName() {
        return name().replace("ROLE_", "");
    }
}

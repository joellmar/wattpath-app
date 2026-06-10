package com.joselumartos.jwtauthbackenddemo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Extraído de SecurityConfig para romper la dependencia circular:
 * SecurityConfig -> OAuth2AuthenticationSuccessHandler -> PasswordEncoder -> (SecurityConfig)
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

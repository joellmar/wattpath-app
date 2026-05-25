package com.joselumartos.jwtauthbackenddemo.config;

import com.joselumartos.jwtauthbackenddemo.security.JwtValidatorFilter;
import com.joselumartos.jwtauthbackenddemo.security.StoreProperties;
import com.joselumartos.jwtauthbackenddemo.security.UserProviderDetailsManager;
import com.joselumartos.jwtauthbackenddemo.services.UserSecurityDetailService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.util.Collections;

@Configuration
@EnableWebSecurity(debug = true)
@EnableMethodSecurity
public class SecurityConfig {

    private final StoreProperties storeProperties;
    private final HandlerExceptionResolver handlerExceptionResolver;

    public SecurityConfig(StoreProperties storeProperties, @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
        this.storeProperties = storeProperties;
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
       http
               .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
               .csrf(csrf -> csrf.disable())
               .cors(cors -> cors.configurationSource(request -> {
                   CorsConfiguration config = new CorsConfiguration();
                   config.setAllowedOrigins(Collections.singletonList("http://localhost:4200"));
                   config.setAllowedMethods(Collections.singletonList("*"));
                   config.setAllowCredentials(true);
                   config.setAllowedHeaders(Collections.singletonList("*"));
                   config.setExposedHeaders(Collections.singletonList("Authorization"));
                   return config;
               }))
               .addFilterBefore(new JwtValidatorFilter(storeProperties, handlerExceptionResolver), BasicAuthenticationFilter.class)
               .authorizeHttpRequests(auth -> auth
                       .requestMatchers("/api/v1/auth/login", "/api/v1/auth/register", "/ws-iot/**").permitAll()
                       .requestMatchers(HttpMethod.GET, "/api/v1/tariffs/**").authenticated()
                       .requestMatchers("/api/v1/tariffs/**").hasRole("ADMIN")
                       .requestMatchers("/admin/**").hasRole("ADMIN")
                       .anyRequest().authenticated()
               )
               .httpBasic(Customizer.withDefaults());

       return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(UserProviderDetailsManager userProviderDetailsManager) {
        return new ProviderManager(userProviderDetailsManager);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

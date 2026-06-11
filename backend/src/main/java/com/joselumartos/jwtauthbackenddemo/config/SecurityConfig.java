package com.joselumartos.jwtauthbackenddemo.config;

import com.joselumartos.jwtauthbackenddemo.security.CookieOAuth2AuthorizationRequestRepository;
import com.joselumartos.jwtauthbackenddemo.security.JwtValidatorFilter;
import com.joselumartos.jwtauthbackenddemo.security.OAuth2AuthenticationSuccessHandler;
import com.joselumartos.jwtauthbackenddemo.security.StoreProperties;
import com.joselumartos.jwtauthbackenddemo.security.UserProviderDetailsManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final StoreProperties storeProperties;
    private final HandlerExceptionResolver handlerExceptionResolver;
    private final CookieOAuth2AuthorizationRequestRepository cookieAuthorizationRequestRepository;
    private final OAuth2AuthenticationSuccessHandler oAuth2SuccessHandler;

    @Value("${app.oauth2.frontend-callback-uri}")
    private String frontendCallbackUri;

    // Orígenes CORS inyectados desde app.cors.allowed-origins (application.properties)
    // En producción Docker se sobreescribe con APP_CORS_ALLOWED_ORIGINS del compose.
    @Value("${app.cors.allowed-origins:http://localhost:4200}")
    private String corsAllowedOrigins;

    public SecurityConfig(
            StoreProperties storeProperties,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver,
            CookieOAuth2AuthorizationRequestRepository cookieAuthorizationRequestRepository,
            OAuth2AuthenticationSuccessHandler oAuth2SuccessHandler) {
        this.storeProperties = storeProperties;
        this.handlerExceptionResolver = handlerExceptionResolver;
        this.cookieAuthorizationRequestRepository = cookieAuthorizationRequestRepository;
        this.oAuth2SuccessHandler = oAuth2SuccessHandler;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(request -> {
                    CorsConfiguration config = new CorsConfiguration();
                    List<String> origins = Arrays.stream(corsAllowedOrigins.split(","))
                            .map(String::trim)
                            .toList();
                    config.setAllowedOrigins(origins);
                    config.setAllowedMethods(Collections.singletonList("*"));
                    config.setAllowCredentials(true);
                    config.setAllowedHeaders(Collections.singletonList("*"));
                    config.setExposedHeaders(Collections.singletonList("Authorization"));
                    return config;
                }))
                .addFilterBefore(
                        new JwtValidatorFilter(storeProperties, handlerExceptionResolver),
                        BasicAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/register",
                                "/api/v1/auth/register/admin",
                                "/api/v1/auth/oauth/exchange",
                                "/oauth2/authorization/**",
                                "/login/oauth2/code/**",
                                "/ws-iot/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/tariffs/**").authenticated()
                        .requestMatchers("/api/v1/tariffs/**").hasRole("ADMIN")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(endpoint -> endpoint
                                .authorizationRequestRepository(cookieAuthorizationRequestRepository))
                        .redirectionEndpoint(endpoint -> endpoint
                                .baseUri("/login/oauth2/code/*"))
                        .successHandler(oAuth2SuccessHandler)
                        .failureHandler((req, res, ex) -> {
                            res.sendRedirect(frontendCallbackUri + "?error=oauth_failed");
                        }))
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(UserProviderDetailsManager userProviderDetailsManager) {
        return new ProviderManager(userProviderDetailsManager);
    }
}

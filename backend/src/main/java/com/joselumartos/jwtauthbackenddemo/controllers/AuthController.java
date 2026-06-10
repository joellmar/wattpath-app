package com.joselumartos.jwtauthbackenddemo.controllers;

import com.joselumartos.jwtauthbackenddemo.dtos.LoginUser;
import com.joselumartos.jwtauthbackenddemo.dtos.LoginUserJwt;
import com.joselumartos.jwtauthbackenddemo.dtos.OAuthTicketExchangeRequest;
import com.joselumartos.jwtauthbackenddemo.dtos.RegisterRequest;
import com.joselumartos.jwtauthbackenddemo.services.AuthRegistrationService;
import com.joselumartos.jwtauthbackenddemo.services.JwtTokenService;
import com.joselumartos.jwtauthbackenddemo.services.OAuth2LoginTicketService;
import com.joselumartos.jwtauthbackenddemo.security.UserProviderDetailsManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserProviderDetailsManager authenticationManager;
    private final JwtTokenService jwtTokenService;
    private final AuthRegistrationService registrationService;
    private final OAuth2LoginTicketService ticketService;

    @Value("${app.admin.secret}")
    private String adminSecret;

    @PostMapping("/login")
    public ResponseEntity<LoginUserJwt> loginUser(@RequestBody LoginUser loginUser) {
        UsernamePasswordAuthenticationToken credentials = UsernamePasswordAuthenticationToken
                .unauthenticated(loginUser.username(), loginUser.password());

        Authentication authentication = authenticationManager.authenticate(credentials);

        if (authentication.isAuthenticated()) {
            String jwt = jwtTokenService.generateJwt(
                    authentication.getName(),
                    authentication.getAuthorities()
            );
            return ResponseEntity.ok(new LoginUserJwt(HttpStatus.OK.toString(), jwt));
        }

        throw new BadCredentialsException("Credenciales de acceso incorrectas.");
    }

    @PostMapping("/register")
    public ResponseEntity<Void> registerUser(@RequestBody RegisterRequest request) {
        registrationService.registerUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/register/admin")
    public ResponseEntity<Void> registerAdmin(
            @RequestBody RegisterRequest request,
            @RequestHeader("X-Wattimizer-Admin-Secret") String secretHeader) {

        if (!adminSecret.equals(secretHeader)) {
            throw new ForbiddenException("Acceso denegado.");
        }

        registrationService.registerAdmin(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Angular llama a este endpoint tras recibir el ticket en la URL de callback.
     * Valida el ticket (un solo uso, 60 s de TTL) y devuelve el JWT corporativo.
     */
    @PostMapping("/oauth/exchange")
    public ResponseEntity<LoginUserJwt> exchangeOAuthTicket(@RequestBody OAuthTicketExchangeRequest exchangeRequest) {
        String jwt = ticketService.consumeTicket(exchangeRequest.ticket());
        return ResponseEntity.ok(new LoginUserJwt(HttpStatus.OK.toString(), jwt));
    }
}

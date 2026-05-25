package com.joselumartos.jwtauthbackenddemo.controllers;

import com.joselumartos.jwtauthbackenddemo.dtos.LoginUser;
import com.joselumartos.jwtauthbackenddemo.dtos.LoginUserJwt;
import com.joselumartos.jwtauthbackenddemo.dtos.RegisterRequest;
import com.joselumartos.jwtauthbackenddemo.entities.Role;
import com.joselumartos.jwtauthbackenddemo.entities.UserEntity;
import com.joselumartos.jwtauthbackenddemo.repositories.TariffRepository;
import com.joselumartos.jwtauthbackenddemo.repositories.UserRepository;
import com.joselumartos.jwtauthbackenddemo.security.StoreProperties;
import com.joselumartos.jwtauthbackenddemo.security.UserProviderDetailsManager;
import com.joselumartos.jwtauthbackenddemo.services.TariffService;
import com.joselumartos.jwtauthbackenddemo.services.UserSecurityDetailService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserProviderDetailsManager authenticationManager;
    private final StoreProperties storeProperties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TariffRepository tariffRepository;

    @Value("${app.admin.secret}")
    private String adminSecret;

    @PostMapping("/login")
    public ResponseEntity<LoginUserJwt> loginUser(@RequestBody LoginUser loginUser) {
        UsernamePasswordAuthenticationToken user = UsernamePasswordAuthenticationToken.unauthenticated(loginUser.username(), loginUser.password());

//        String hashParaBD = new BCryptPasswordEncoder().encode("12345");
//        System.out.println("COPIA ESTE HASH EXACTO EN TU BASE DE DATOS: " + hashParaBD);

        Authentication authentication = authenticationManager.authenticate(user);

        if (authentication.isAuthenticated()) {
            try {
                String secret = storeProperties.jwtSecretKeyValue();
                SecretKey secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
                String jwt = Jwts.builder()
                        .issuer("store-security")
                        .subject("JWT Token")
                        .claim("username", authentication.getName())
                        .claim("authorities", authentication.getAuthorities()
                                .stream()
                                .map(GrantedAuthority::getAuthority)
                                .collect(Collectors.joining(",")))
                        .issuedAt(new Date())
                        .expiration(new Date(new Date().getTime() + 8 * 60 * 60 * 1000))
                        .signWith(secretKey)
                        .compact();

                return ResponseEntity.ok(new LoginUserJwt(HttpStatus.OK.toString(), jwt));
            } catch (Exception e) {
                throw new BadCredentialsException(
                        String.format("Bad credentials %s for user %s", e.getMessage(), authentication.getName()));
            }
        }

        throw new BadCredentialsException(
                String.format("Bad credentials for user %s", authentication.getName())
        );
    }

    @PostMapping("/register")
    public ResponseEntity<String> registerUser(@RequestBody RegisterRequest request) {
        Optional<UserEntity> userOpt = userRepository.findByUsername(request.username());
        if (userOpt.isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("El email/nombre de usuario ya está registrado en Wattimizer.");
        }

        UserEntity user = new UserEntity();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(Role.ROLE_USER);
        user.setEnabled(true);

        if (request.tariffId() != null) {
            tariffRepository.findById(request.tariffId()).ifPresent(user::setTariff);
        }

        userRepository.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).body("Usuario registrado con éxito.");
    }

    @PostMapping("/register/admin")
    public ResponseEntity<String> registerAdmin(
            @RequestBody RegisterRequest request,
            @RequestHeader("X-Wattimizer-Admin-Secret") String secretHeader
    ) {
        if (secretHeader == null || !secretHeader.equals(adminSecret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Acceso denegado: Secreto de plataforma inválido.");
        }

        Optional<UserEntity> userOpt = userRepository.findByUsername(request.username());
        if (userOpt.isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("El nombre de usuario administrador ya existe.");
        }

        UserEntity adminUser = new UserEntity();
        adminUser.setUsername(request.username());
        adminUser.setPassword(passwordEncoder.encode(request.password()));
        adminUser.setRole(Role.ROLE_ADMIN);
        adminUser.setEnabled(true);

        userRepository.save(adminUser);
        return ResponseEntity.status(HttpStatus.CREATED).body("Administrador del sistema registrado con éxito.");
    }
}

package com.joselumartos.jwtauthbackenddemo.security;

import com.joselumartos.jwtauthbackenddemo.entities.FederatedIdentity;
import com.joselumartos.jwtauthbackenddemo.entities.OAuthProvider;
import com.joselumartos.jwtauthbackenddemo.entities.Role;
import com.joselumartos.jwtauthbackenddemo.entities.UserEntity;
import com.joselumartos.jwtauthbackenddemo.repositories.FederatedIdentityRepository;
import com.joselumartos.jwtauthbackenddemo.repositories.UserRepository;
import com.joselumartos.jwtauthbackenddemo.services.JwtTokenService;
import com.joselumartos.jwtauthbackenddemo.services.OAuth2LoginTicketService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.UUID;

/**
 * Ejecutado tras la validación exitosa del código OAuth2 por parte del proveedor.
 *
 * Responsabilidades:
 * 1. Extraer y verificar el email del proveedor.
 * 2. Encontrar o crear el UserEntity local asociado.
 * 3. Registrar (o actualizar) el vínculo en federated_identities.
 * 4. Emitir el JWT corporativo y generar un ticket de un solo uso.
 * 5. Redirigir al frontend con el ticket (nunca con el JWT en query string).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenService jwtTokenService;
    private final OAuth2LoginTicketService ticketService;
    private final UserRepository userRepository;
    private final FederatedIdentityRepository federatedIdentityRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.oauth2.frontend-callback-uri}")
    private String frontendCallbackUri;

    @Override
    @Transactional
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException {

        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User principal = oauthToken.getPrincipal();
        String registrationId = oauthToken.getAuthorizedClientRegistrationId();

        OAuthProvider provider;
        try {
            provider = OAuthProvider.valueOf(registrationId.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Proveedor OAuth2 no soportado: {}", registrationId);
            response.sendRedirect(frontendCallbackUri + "?error=unsupported_provider");
            return;
        }

        String verifiedEmail = resolveVerifiedEmail(principal, provider);
        if (verifiedEmail == null) {
            log.warn("El proveedor {} no entregó un email verificado para el subject {}",
                    provider, principal.getName());
            response.sendRedirect(frontendCallbackUri + "?error=email_not_verified");
            return;
        }

        UserEntity user = findOrCreateUser(provider, principal.getName(), verifiedEmail);
        String jwt = jwtTokenService.generateJwt(user.getUsername(), user.getAuthorities());
        String ticket = ticketService.createTicket(jwt);

        response.sendRedirect(frontendCallbackUri + "?ticket=" + ticket);
    }

    /**
     * Extrae el email verificado del principal.
     * Google (OIDC): el claim email_verified debe ser true.
     * GitHub: si el scope user:email está concedido, el atributo email contiene
     *         el email primario verificado; null indica perfil sin email público.
     */
    private String resolveVerifiedEmail(OAuth2User principal, OAuthProvider provider) {
        if (provider == OAuthProvider.GOOGLE) {
            OidcUser oidcUser = (OidcUser) principal;
            if (!Boolean.TRUE.equals(oidcUser.getEmailVerified())) {
                return null;
            }
            return oidcUser.getEmail().trim().toLowerCase();
        }

        // GitHub
        String email = principal.getAttribute("email");
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    private UserEntity findOrCreateUser(OAuthProvider provider, String providerSubject, String normalizedEmail) {
        return federatedIdentityRepository
                .findByProviderAndProviderSubject(provider, providerSubject)
                .map(FederatedIdentity::getUser)
                .orElseGet(() -> {
                    // Email no vinculado al proveedor: buscar por email o crear usuario nuevo
                    UserEntity user = userRepository.findByUsername(normalizedEmail)
                            .orElseGet(() -> {
                                UserEntity newUser = new UserEntity();
                                newUser.setUsername(normalizedEmail);
                                // Contraseña aleatoria: este usuario solo puede autenticarse vía OAuth2
                                newUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
                                newUser.setRole(Role.ROLE_USER);
                                newUser.setEnabled(true);
                                return userRepository.save(newUser);
                            });

                    FederatedIdentity identity = new FederatedIdentity();
                    identity.setUser(user);
                    identity.setProvider(provider);
                    identity.setProviderSubject(providerSubject);
                    identity.setEmailAtLogin(normalizedEmail);
                    federatedIdentityRepository.save(identity);

                    return user;
                });
    }
}

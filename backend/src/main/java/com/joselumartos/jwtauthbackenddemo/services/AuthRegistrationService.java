package com.joselumartos.jwtauthbackenddemo.services;

import com.joselumartos.jwtauthbackenddemo.dtos.RegisterRequest;
import com.joselumartos.jwtauthbackenddemo.entities.Role;
import com.joselumartos.jwtauthbackenddemo.entities.UserEntity;
import com.joselumartos.jwtauthbackenddemo.repositories.TariffRepository;
import com.joselumartos.jwtauthbackenddemo.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Encapsula las reglas de negocio del alta de usuarios.
 * El backend no confía en las validaciones del frontend:
 * valida nulos, formato de email, longitud de contraseña y confirmación antes de persistir.
 */
@Service
@RequiredArgsConstructor
public class AuthRegistrationService {

    private final UserRepository userRepository;
    private final TariffRepository tariffRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void registerUser(RegisterRequest request) {
        validateRequest(request);
        String normalizedEmail = request.username().trim().toLowerCase();

        if (userRepository.findByUsername(normalizedEmail).isPresent()) {
            throw new IllegalStateException("Ya existe una cuenta registrada con ese correo electrónico.");
        }

        UserEntity user = buildUser(normalizedEmail, request.password(), Role.ROLE_USER);

        if (request.tariffId() != null) {
            tariffRepository.findById(request.tariffId()).ifPresent(user::setTariff);
        }

        userRepository.save(user);
    }

    @Transactional
    public void registerAdmin(RegisterRequest request) {
        validateRequest(request);
        String normalizedEmail = request.username().trim().toLowerCase();

        if (userRepository.findByUsername(normalizedEmail).isPresent()) {
            throw new IllegalStateException("El nombre de usuario administrador ya existe.");
        }

        userRepository.save(buildUser(normalizedEmail, request.password(), Role.ROLE_ADMIN));
    }

    private void validateRequest(RegisterRequest request) {
        if (request.username() == null || request.username().isBlank()) {
            throw new IllegalStateException("El correo electrónico es obligatorio.");
        }
        if (!isValidEmail(request.username().trim())) {
            throw new IllegalStateException("El formato del correo electrónico no es válido.");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new IllegalStateException("La contraseña es obligatoria.");
        }
        if (request.password().length() < 6) {
            throw new IllegalStateException("La contraseña debe tener al menos 6 caracteres.");
        }
        if (request.confirmPassword() == null || request.confirmPassword().isBlank()) {
            throw new IllegalStateException("La confirmación de contraseña es obligatoria.");
        }
        if (!request.password().equals(request.confirmPassword())) {
            throw new IllegalStateException("Las contraseñas no coinciden.");
        }
    }

    private boolean isValidEmail(String email) {
        return email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    }

    private UserEntity buildUser(String normalizedEmail, String rawPassword, Role role) {
        UserEntity user = new UserEntity();
        user.setUsername(normalizedEmail);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setEnabled(true);
        return user;
    }
}

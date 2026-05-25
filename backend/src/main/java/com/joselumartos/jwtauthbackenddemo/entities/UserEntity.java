package com.joselumartos.jwtauthbackenddemo.entities;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "users")
@NoArgsConstructor
@Setter
@Getter
public class UserEntity extends BaseEntity implements UserDetails {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tariff_id") // Puede ser nullable si un admin no tiene tarifa
    private Tariff tariff;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    // Se guarda como texto en la base de datos ("ROLE_USER")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "active", nullable = false)
    private boolean enabled; // Opcional: Para desactivar usuarios

    public UserEntity(String username, String password, Role role) {
        this.username = username;
        this.password = password;
        this.role = role;
    }

    @Override
    public String getUsername() {
        return this.username;
    }

    @Override
    public String getPassword() {
        return this.password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(this.role.name()));
    }

    // Indica si la cuenta ha caducado. Es una cuenta activa, así que devuelve true.
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    // Indica si la cuenta está bloqueada. No lo está, así que devuelve true.
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    // Indica si la contraseña ha caducado. No ha caducado, devuelve true.
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    // Indica si el usuario está habilitado (activo).
    @Override
    public boolean isEnabled() {
        return this.enabled;
//        return true;
    }
}

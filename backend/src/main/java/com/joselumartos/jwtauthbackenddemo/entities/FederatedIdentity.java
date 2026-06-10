package com.joselumartos.jwtauthbackenddemo.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Vincula una identidad de proveedor externo (Google, GitHub) con un UserEntity local.
 * El par (provider, providerSubject) identifica de forma única a un usuario en un proveedor
 * y resiste colisiones de email entre proveedores distintos.
 */
@Entity
@Table(
    name = "federated_identities",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_federated_provider_subject",
        columnNames = {"provider", "provider_subject"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class FederatedIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OAuthProvider provider;

    @Column(name = "provider_subject", nullable = false)
    private String providerSubject;

    /**
     * Email que el proveedor reportó en el momento del login.
     * Puede diferir del username si el usuario cambia su email en el proveedor.
     */
    @Column(name = "email_at_login")
    private String emailAtLogin;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}

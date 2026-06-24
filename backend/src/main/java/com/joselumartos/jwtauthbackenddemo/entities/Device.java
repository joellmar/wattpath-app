package com.joselumartos.jwtauthbackenddemo.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "devices")
@NoArgsConstructor
@Getter
@Setter
public class Device extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @Column(nullable = false)
    private String name;

    @Column(name = "mac_address", unique = true, nullable = false)
    private String macAddress;

    @Column(name = "is_on")
    private Boolean isOn;

    // columnDefinition con DEFAULT evita que Hibernate falle al añadir la columna NOT NULL sobre filas existentes
    @Column(name = "is_simulated", nullable = false, columnDefinition = "boolean default false")
    private Boolean simulated = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "simulation_profile")
    private SimulationProfile simulationProfile;
}

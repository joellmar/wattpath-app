package com.joselumartos.jwtauthbackenddemo.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tariffs")
@NoArgsConstructor
@Getter
@Setter
public class Tariff extends BaseEntity {

    @Column(nullable = false)
    private String name;

    // Mercado contractual: "PVPC" o libre.
    private String market;

    // Peaje de acceso (2.0TD, 3.0TD, 6.1TD, 6.2TD). Única fuente de verdad para el tipo de tarifa.
    @Column(name = "access_tariff_code", nullable = false, length = 10)
    private String accessTariffCode;

    // Zona geográfica española, necesaria para resolver temporadas regulatorias en tariff_calendar_slots.
    @Column(name = "geographic_zone", nullable = false, length = 20)
    private String geographicZone = "PENINSULA";

    private String energyCompany;

    // Precios de energía por periodo P1-P6 para este contrato.
    @OneToMany(mappedBy = "tariff", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Period> periods = new ArrayList<>();

    // Potencias contratadas independientes por periodo P1-P6.
    @OneToMany(mappedBy = "tariff", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TariffContractedPower> contractedPowers = new ArrayList<>();
}

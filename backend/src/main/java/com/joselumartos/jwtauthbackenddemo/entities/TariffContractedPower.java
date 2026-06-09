package com.joselumartos.jwtauthbackenddemo.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

// Potencia contratada (kW) para un periodo P1-P6 dentro de un contrato de tarifa.
// Para 3.0TD, 6.1TD y 6.2TD, cada periodo tiene un valor independiente (P1 <= P2 <= ... <= P6).
// La validación del orden se delega al servicio, no a un CHECK por fila.
@Entity
@Table(
        name = "tariff_contracted_powers",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_tariff_contracted_powers_tariff_period",
                columnNames = {"tariff_id", "period_code"}
        )
)
@NoArgsConstructor
@Getter
@Setter
public class TariffContractedPower {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "tariff_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_tariff_contracted_powers_tariff")
    )
    private Tariff tariff;

    @Column(name = "period_code", nullable = false, length = 2)
    private String periodCode;

    @Column(name = "contracted_power_kw", nullable = false, precision = 10, scale = 2)
    private BigDecimal contractedPowerKw;
}

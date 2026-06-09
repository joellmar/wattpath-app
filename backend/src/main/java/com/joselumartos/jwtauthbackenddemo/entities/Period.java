package com.joselumartos.jwtauthbackenddemo.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

// Precio contractual de energía (€/kWh) para un periodo regulatorio P1-P6 dentro de una tarifa.
// Los horarios ya NO viven aquí: residen en tariff_calendar_slots.
@Entity
@Table(
        name = "periods",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_periods_tariff_period_code",
                columnNames = {"tariff_id", "period_code"}
        )
)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Period {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Obligatorio: un precio por periodo no tiene sentido sin su tarifa propietaria.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "tariff_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_periods_tariff")
    )
    private Tariff tariff;

    // Código del periodo regulatorio: P1, P2, P3, P4, P5 o P6.
    // El join con precio se resuelve por este campo, no por rangos horarios embebidos.
    @Column(name = "period_code", nullable = false, length = 2)
    private String periodCode;

    // Precio energético contractual en €/kWh con precisión regulatoria.
    @Column(name = "price_kwh", nullable = false, precision = 10, scale = 6)
    private BigDecimal priceKwh;
}

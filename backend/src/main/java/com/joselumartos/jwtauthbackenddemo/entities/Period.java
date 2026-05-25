package com.joselumartos.jwtauthbackenddemo.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalTime;

@Entity
@Table(name = "periods")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Period {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tariff_id", nullable = false)
    private Tariff tariff;

    @Column(nullable = false)
    private String name;

    @Column(name = "price_kwh", nullable = false, precision = 10, scale = 6)
    private BigDecimal priceKwh;

    @Column(name = "start_hour")
    private LocalTime startHour;

    @Column(name = "end_hour")
    private LocalTime endHour;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_type")
    private DayType dayType;

    @Column(name = "start_month")
    private Integer startMonth;

    @Column(name = "end_month")
    private Integer endMonth;
}

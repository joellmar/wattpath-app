package com.joselumartos.jwtauthbackenddemo.entities;

import com.joselumartos.jwtauthbackenddemo.dtos.EventsRpc;
import com.joselumartos.jwtauthbackenddemo.dtos.Status;
import com.joselumartos.jwtauthbackenddemo.mappers.EventsRpcMapper;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "readings")
@IdClass(ReadingId.class) // <--- Conecta con la clase PK compuesta
@NoArgsConstructor
@Getter
@Setter
public class Reading {

    @Id
    @Column(nullable = false, updatable = false)
    private Instant time; // Instant maneja perfectamente las zonas horarias (UTC)

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Column(name = "power_w", precision = 10, scale = 2)
    private BigDecimal powerW;

    @Column(name = "energy_total_kwh", precision = 14, scale = 4)
    private BigDecimal energyTotalKwh;

    @Column(name = "is_on")
    private Boolean isOn;
}

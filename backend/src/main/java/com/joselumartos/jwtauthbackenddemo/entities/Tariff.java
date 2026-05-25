package com.joselumartos.jwtauthbackenddemo.entities;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
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

    private String type;

    private String market;

    @Column(name = "contracted_power_kw", precision = 10, scale = 2)
    private BigDecimal contractedPowerKw;

    private String energyCompany;

    @OneToMany(mappedBy = "tariff", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Period> periods = new ArrayList<>();
}

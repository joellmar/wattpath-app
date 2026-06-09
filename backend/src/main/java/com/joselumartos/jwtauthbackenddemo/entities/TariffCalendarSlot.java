package com.joselumartos.jwtauthbackenddemo.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

// Tabla regulatoria que resuelve zona + mes + tipo_día + hora_local -> period_code.
// Es una tabla de dimensión global (no pertenece a ningún usuario ni tarifa concreta).
// Los datos los publica REE/CNMC y se cargan una sola vez via script de seed.
@Entity
@Table(name = "tariff_calendar_slots")
@NoArgsConstructor
@Getter
@Setter
public class TariffCalendarSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Discriminador de peaje: 2.0TD, 3.0TD, 6.1TD, 6.2TD.
    // Sin este campo, los slots de 2.0TD y 3.0TD colisionan para la misma zona+mes+hora
    // porque sus tablas de periodos son distintas (P1-P3 vs P1-P6).
    @Column(name = "access_tariff_code", nullable = false, length = 10)
    private String accessTariffCode;

    // Zona geográfica española: PENINSULA, CANARIAS, ISLAS_BALEARES, CEUTA, MELILLA.
    @Column(name = "geographic_zone", nullable = false, length = 20)
    private String geographicZone;

    // Número de mes (1-12). Evita codificar en Java la equivalencia zona + mes -> temporada.
    @Column(name = "month_number", nullable = false)
    private Integer monthNumber;

    // Temporada regulatoria derivada de zona + mes: HIGH, MID_HIGH, MID, LOW.
    @Column(name = "season_code", nullable = false, length = 12)
    private String seasonCode;

    // Tipo de día: A (alta temporada laborable), B, B1, C, D (festivo/fin de semana -> P6 todo el día).
    @Column(name = "day_type", nullable = false, length = 2)
    private String dayType;

    // Periodo aplicable dentro de este slot horario.
    @Column(name = "period_code", nullable = false, length = 2)
    private String periodCode;

    // Semántica: intervalo semiabierto [startTime, endTime).
    // Excepción: type D, P6 puede tener startTime = endTime para representar el día completo.
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;
}

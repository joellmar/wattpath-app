# Anexo D. TimescaleDB y analítica energética

## 1. Papel de la base de datos

Wattimizer usa PostgreSQL como base relacional y TimescaleDB para optimizar la tabla de lecturas temporales. La mayor parte del modelo es relacional clasico: usuarios, dispositivos, tarifas, periodos y alertas. La excepcion importante es `readings`, porque recibe datos cada pocos segundos desde MQTT o desde simuladores.

La aplicación no usa Flyway ni Liquibase. El esquema base lo crea Hibernate con `spring.jpa.hibernate.ddl-auto=update` y despues se ejecutan scripts SQL manuales para activar extensiones, convertir `readings` en hypertable y anadir constraints especificos.

## 2. Orden de inicializacion SQL

| Orden | Script | Funcion |
|---|---|---|
| 1 | `backend/src/main/resources/db/dev-seed/00-extensions.sql` | Activa `timescaledb` y `pgcrypto`. |
| 2 | `backend/src/main/resources/db/dev-seed/01-hypertable.sql` | Convierte `readings` en hypertable por columna `time`. |
| 3 | `backend/src/main/resources/db/tariffs-td-schema.sql` | Ajusta columnas, checks e indices de tarifas TD. |
| 4 | `backend/src/main/resources/db/seed-tariff-calendar-slots.sql` | Inserta calendario regulatorio para `2.0TD` y `3.0TD` en `PENINSULA` e `ISLAS_BALEARES`. |
| 5 | `backend/src/main/resources/db/dev-seed/03-seed-users-dev.sql` | Crea usuarios de desarrollo. |
| 6 | `backend/src/main/resources/db/dev-seed/04-seed-device-shelly.sql` | Inserta Shelly de pruebas. |
| 7 | `backend/src/main/resources/db/dev-seed/05-seed-device-simulation.sql` | Inserta dispositivos simulados. |
| 8 | `backend/src/main/resources/db/prod/99-resync-sequences.sql` | Resincroniza secuencias tras seeds. |

El script de hypertable avisa de una condicion clave: debe ejecutarse antes de que entren datos MQTT. Si ya hay filas, habria que usar `migrate_data => true`.

```sql
CREATE EXTENSION IF NOT EXISTS timescaledb;

SELECT create_hypertable('readings', 'time');
```

Verificacion:

```sql
SELECT hypertable_name, num_chunks
FROM timescaledb_information.hypertables;
```

## 3. Modelo relacional

### 3.1. `users`

Entidad: `UserEntity`

| Columna | Tipo Java | Comentario |
|---|---|---|
| `id` | `Long` | Heredado de `BaseEntity`. |
| `username` | `String` | Email/login, único y obligatorio. |
| `password` | `String` | Hash BCrypt. |
| `role` | `Role` | `ROLE_USER` o `ROLE_ADMIN`. |
| `active` | `boolean` | Control de cuenta habilitada. |
| `tariff_id` | `Tariff` | Tarifa privada o plantilla asociada al usuario. |

### 3.2. `devices`

Entidad: `Device`

| Columna | Tipo Java | Comentario |
|---|---|---|
| `id` | `Long` | PK heredada. |
| `user_id` | `UserEntity` | Nullable; permite dispositivos no reclamados. |
| `name` | `String` | Nombre visible del medidor. |
| `mac_address` | `String` | Unico y obligatorio. |
| `is_on` | `Boolean` | Estado del dispositivo. |
| `is_simulated` | `Boolean` | Indica si genera telemetría desde el backend. |
| `simulation_profile` | `SimulationProfile` | Perfil de consumo simulado. |

### 3.3. `readings`

Entidad: `Reading`

| Columna | Tipo Java | Comentario |
|---|---|---|
| `time` | `Instant` | Parte de la PK compuesta y eje temporal de TimescaleDB. |
| `device_id` | `Device` | Parte de la PK compuesta. |
| `power_w` | `BigDecimal(10,2)` | Potencia instantanea en vatios. |
| `energy_total_kwh` | `BigDecimal(14,4)` | Energia acumulada del dispositivo. |
| `is_on` | `Boolean` | Estado del enchufe al registrar la lectura. |

La clave compuesta `(time, device_id)` evita que dos lecturas del mismo dispositivo ocupen el mismo instante exacto. En los simuladores se anade un pequeno desfase por dispositivo para reducir colisiones.

### 3.4. `tariffs`

Entidad: `Tariff`

| Columna | Tipo Java | Comentario |
|---|---|---|
| `id` | `Long` | PK heredada. |
| `name` | `String` | Nombre de tarifa. |
| `market` | `String` | Mercado contractual, por ejemplo PVPC o libre. |
| `access_tariff_code` | `String` | Peaje: `2.0TD`, `3.0TD`, `6.1TD`, `6.2TD`. |
| `geographic_zone` | `String` | Zona: `PENINSULA`, `CANARIAS`, `ISLAS_BALEARES`, `CEUTA`, `MELILLA`. |
| `energy_company` | `String` | Comercializadora. |

Relaciones:

- `tariffs` 1:N `periods`
- `tariffs` 1:N `tariff_contracted_powers`
- `users` N:1 `tariffs`

### 3.5. `periods`

Entidad: `Period`

| Columna | Tipo Java | Comentario |
|---|---|---|
| `id` | `Long` | PK. |
| `tariff_id` | `Tariff` | FK obligatoria. |
| `period_code` | `String` | P1-P6. |
| `price_kwh` | `BigDecimal(10,6)` | Precio contractual por kWh. |

Índice único:

```sql
CREATE UNIQUE INDEX IF NOT EXISTS ux_periods_tariff_period_code
  ON periods (tariff_id, period_code);
```

### 3.6. `tariff_contracted_powers`

Entidad: `TariffContractedPower`

| Columna | Tipo Java | Comentario |
|---|---|---|
| `id` | `Long` | PK. |
| `tariff_id` | `Tariff` | FK obligatoria. |
| `period_code` | `String` | P1-P6. |
| `contracted_power_kw` | `BigDecimal(10,2)` | Potencia contratada por periodo. |

El orden creciente de potencias se válida en servicio, no con un `CHECK` por fila.

### 3.7. `tariff_calendar_slots`

Entidad: `TariffCalendarSlot`

Esta tabla es una dimension regulatoria global. No pertenece a ningun usuario ni tarifa concreta; sirve para resolver que periodo aplica en una fecha y hora local.

| Columna | Funcion |
|---|---|
| `access_tariff_code` | Distingue `2.0TD`, `3.0TD`, `6.1TD`, `6.2TD`. |
| `geographic_zone` | Zona geografica española. |
| `month_number` | Mes 1-12. |
| `season_code` | Temporada: `HIGH`, `MID_HIGH`, `MID`, `LOW`. |
| `day_type` | Tipo de dia: `A`, `B`, `B1`, `C`, `D`. |
| `period_code` | Periodo resultante P1-P6. |
| `start_time`, `end_time` | Intervalo horario local. |

El índice principal de busqueda es:

```sql
CREATE UNIQUE INDEX IF NOT EXISTS ux_tariff_calendar_slots_lookup
  ON tariff_calendar_slots (
    access_tariff_code, geographic_zone, month_number, day_type, start_time, end_time
  );
```

### 3.8. `alerts`

Entidad: `Alert`

| Columna | Tipo Java | Comentario |
|---|---|---|
| `id` | `Long` | PK heredada. |
| `user_id` | `UserEntity` | Usuario que recibe la alerta. |
| `device_id` | `Device` | Dispositivo que provoca la alerta. |
| `type` | `String` | Actualmente `OVERPOWER`. |
| `message` | `String` | Texto de alerta. |

### 3.9. `federated_identities`

Entidad: `FederatedIdentity`

Vincula cuentas externas con usuarios locales. Tiene un constraint único sobre `(provider, provider_subject)` para evitar duplicados cuando el usuario entra con Google o GitHub.

## 4. Consultas sobre lecturas

`ReadingRepository` contiene la consulta temporal principal:

```java
@Query("SELECT r FROM Reading r WHERE r.device.macAddress = :macAddress " +
        "AND r.time >= :start AND r.time <= :end ORDER BY r.time ASC")
List<Reading> findReadingsInInterval(
        @Param("macAddress") String macAddress,
        @Param("start") Instant start,
        @Param("end") Instant end);
```

Usos:

- Historico reciente del dashboard.
- Calculo de coste total.
- Calculo de consumo fantasma.

Tambien hay consultas derivadas:

```java
Optional<Reading> findFirstByDeviceMacAddressOrderByTimeDesc(String macAddress);
Optional<Reading> findByTimeAndDeviceMacAddress(Instant time, String macAddress);
```

Y borrados especificos:

```java
@Query("DELETE FROM Reading r WHERE r.time = :time AND r.device.macAddress = :macAddress")
Long deleteByTimeAndDeviceMacAddress(...);

@Query("DELETE FROM Reading r WHERE r.device.macAddress = :macAddress")
int deleteAllByDeviceMacAddress(...);
```

## 5. Analitica de coste

La analítica actual esta en `ConsumptionService`. No usa `time_bucket`, continuous aggregates ni funciones especificas de TimescaleDB. TimescaleDB aporta particionado temporal, pero el calculo se hace en Java.

### 5.1. Coste total

Metodo: `calculateCostInPeriod(String macAddress, Instant start, Instant end)`

Flujo:

1. Lee lecturas ordenadas con `findReadingsInInterval`.
2. Comprueba que hay al menos dos lecturas.
3. Busca el dispositivo y su tarifa.
4. Recorre pares de lecturas consecutivas.
5. Calcula `deltaKwh = current.energyTotalKwh - previous.energyTotalKwh`.
6. Descarta deltas nulos, negativos o cero para evitar reinicios de odometro.
7. Resuelve el periodo P1-P6 aplicable al instante de la lectura.
8. Multiplica `deltaKwh * priceKwh`.
9. Devuelve el total con escala de 2 decimales.

```java
private Optional<BigDecimal> calculatePositiveDelta(Reading previous, Reading current) {
    if (current.getEnergyTotalKwh() == null || previous.getEnergyTotalKwh() == null) {
        return Optional.empty();
    }
    BigDecimal delta = current.getEnergyTotalKwh().subtract(previous.getEnergyTotalKwh());
    return delta.compareTo(BigDecimal.ZERO) > 0 ? Optional.of(delta) : Optional.empty();
}
```

### 5.2. Consumo fantasma

Metodo: `calculateGhostCost(String macAddress, Instant start, Instant end)`

La diferencia con el coste total es que solo suma lecturas cuya hora local cae entre las 00:00 y las 05:59. Esta ventana no equivale necesariamente al periodo valle regulatorio; se usa como definicion funcional de inactividad nocturna.

```java
private boolean isGhostWindow(Tariff tariff, Instant instant) {
    ZoneId zoneId = calendarResolverService.resolveZoneIdForTariff(tariff);
    int hour = instant.atZone(zoneId).getHour();
    return hour >= 0 && hour < 6;
}
```

### 5.3. Coste instantaneo

Metodo: `calculateInstantaneousCost(String macAddress, double powerW, int durationSeconds)`

Formula:

```text
kWh = (powerW / 1000) * (durationSeconds / 3600)
coste = kWh * priceKwh
```

Este metodo es util para estimaciones por muestra, aunque los endpoints de analítica actuales usan principalmente deltas del acumulado `energyTotalKwh`.

## 6. Resolucion del periodo tarifario

`CalendarResolverService` convierte un `Instant` a periodo tarifario:

1. Determina zona horaria desde la zona del contrato.
2. Convierte el instante UTC a hora local.
3. Obtiene el tipo de dia: fines de semana son `D`; laborables consultan temporada.
4. Llama a `TariffCalendarSlotRepository.findPeriodCode(...)`.
5. Con el `periodCode`, busca el precio en `PeriodRepository.findByTariffIdAndPeriodCode(...)`.

Consulta JPQL principal:

```java
@Query("""
        SELECT cs.periodCode FROM TariffCalendarSlot cs
        WHERE cs.accessTariffCode = :accessTariffCode
          AND cs.geographicZone   = :zone
          AND cs.monthNumber      = :month
          AND cs.dayType          = :dayType
          AND (
                (cs.startTime <> cs.endTime AND cs.startTime <= :localTime AND cs.endTime > :localTime)
                OR
                (cs.startTime = cs.endTime AND cs.dayType = 'D')
                OR (cs.endTime = :endOfDay AND :localTime >= cs.startTime)
              )
        """)
Optional<String> findPeriodCode(...);
```

La semantica normal es intervalo semiabierto `[startTime, endTime)`. La excepcion `startTime = endTime` se reserva para dias `D` que representan dia completo.

## 7. Endpoints que consumen analítica

| Endpoint | Parametros | Servicio |
|---|---|---|
| `GET /api/v1/analytics/cost` | `macAddress`, `start`, `end` | `calculateCostInPeriod` |
| `GET /api/v1/analytics/ghost-consumption` | `macAddress`, `start`, `end` | `calculateGhostCost` |
| `GET /api/v1/readings/device/{macAddress}/recent?seconds=120` | `macAddress`, `seconds` | `findReadingsInInterval` |

Angular llama a estos endpoints desde `DashboardComponent` cuando hay una MAC seleccionada y el usuario tiene tarifa privada.

## 8. Relacion con TimescaleDB

La hypertable `readings` mejora el comportamiento de una tabla que crece por tiempo. TimescaleDB divide internamente los datos en chunks, lo que facilita mantener rendimiento en inserciones y consultas por rango temporal.

Actualmente no hay en el repositorio:

- `time_bucket(...)`
- continuous aggregates
- politicas de compresion
- politicas de retencion
- vistas materializadas de consumo diario

Esto no impide que el MVP funcione, pero marca una mejora clara. El siguiente paso natural seria mover parte de la analítica a SQL, por ejemplo:

```sql
SELECT time_bucket('15 minutes', time) AS bucket,
       device_id,
       avg(power_w) AS avg_power_w,
       max(power_w) AS max_power_w
FROM readings
WHERE time >= :start
  AND time <= :end
GROUP BY bucket, device_id
ORDER BY bucket;
```

Este ejemplo no esta implementado en el backend actual; se incluye como linea futura porque encaja con el uso de TimescaleDB y reduciria carga en Java para historicos grandes.

## 9. Consideraciones de integridad

- `devices.mac_address` es único, así se evita registrar dos veces el mismo medidor.
- `periods` tiene unicidad por `(tariff_id, period_code)`.
- `tariff_contracted_powers` tiene unicidad por `(tariff_id, period_code)`.
- `tariff_calendar_slots` usa checks para peaje, zona, mes, temporada, tipo de dia y periodo.
- Al borrar un dispositivo, `DeviceService.deleteById` elimina antes lecturas y alertas para evitar errores de clave foránea.

## 10. Riesgos y mejoras

- `spring.jpa.hibernate.ddl-auto=update` es comodo para desarrollo, pero en producción seria más controlable usar migraciones versionadas.
- `ReadingService.listByUsername()` filtra en memoria; deberia moverse a una consulta por usuario si la tabla crece.
- Las consultas analíticas cargan lecturas completas en Java; TimescaleDB permitiria agregaciones por tramo temporal mucho más eficientes.
- La cobertura de calendario seed esta centrada en `2.0TD` y `3.0TD` para `PENINSULA` e `ISLAS_BALEARES`; otros códigos/zonas estan contemplados por constraints y entidades, pero necesitan datos completos para calcular correctamente.

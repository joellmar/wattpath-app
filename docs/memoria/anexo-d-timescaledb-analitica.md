# Anexo D. TimescaleDB, modelo de datos y consultas analiticas

Este anexo describe la estructura de tablas y las consultas analiticas usadas por Wattimizer. La base se apoya en PostgreSQL con TimescaleDB para las lecturas temporales.

## 1. Enfoque de persistencia

El proyecto mezcla dos tipos de datos:

- **Datos relacionales:** usuarios, dispositivos, tarifas, periodos y alertas.
- **Datos temporales:** lecturas de potencia y energia en la tabla `readings`.

Hibernate crea las tablas mediante `spring.jpa.hibernate.ddl-auto=update`. Despues, los scripts SQL completan lo que JPA no puede expresar bien:

- Extension TimescaleDB.
- Conversion de `readings` en hypertable.
- Constraints regulatorios de tarifas TD.
- Indices especificos para calendario tarifario.

## 2. Orden de scripts SQL

| Script | Funcion |
| --- | --- |
| `db/dev-seed/00-extensions.sql` | Activa extensiones como TimescaleDB y `pgcrypto`. |
| `db/dev-seed/01-hypertable.sql` | Convierte `readings` en hypertable por `time`. |
| `db/tariffs-td-schema.sql` | Ajusta columnas, constraints e indices de tarifas TD. |
| `db/seed-tariff-calendar-slots.sql` | Carga calendario regulatorio 2.0TD y 3.0TD para Peninsula e Islas Baleares. |
| `db/dev-seed/03-seed-users-dev.sql` | Usuarios de desarrollo. |
| `db/dev-seed/04-seed-device-shelly.sql` | Dispositivo Shelly de desarrollo. |
| `db/dev-seed/05-seed-device-simulation.sql` | Dispositivos simulados. |
| `db/prod/99-resync-sequences.sql` | Resincroniza secuencias tras seeds con IDs explicitos. |

## 3. Hypertable `readings`

Script:

```sql
SELECT create_hypertable('readings', 'time');
```

La tabla debe estar vacia cuando se ejecuta. Si ya tuviera datos, el propio script deja indicada la alternativa:

```sql
SELECT create_hypertable('readings', 'time', migrate_data => true);
```

Verificacion:

```sql
SELECT hypertable_name, num_chunks
FROM timescaledb_information.hypertables;
```

Resultado esperado:

```text
readings | 0
```

La decision de particionar por `time` tiene sentido porque todas las consultas analiticas importantes filtran por intervalo temporal.

## 4. Tabla `readings`

Entidad Java: `Reading`

| Columna | Tipo conceptual | Uso |
| --- | --- | --- |
| `time` | `Instant` | Instante de la medicion. Forma parte de la PK. |
| `device_id` | FK a `devices` | Dispositivo que genero la lectura. Forma parte de la PK. |
| `power_w` | decimal | Potencia instantanea en vatios. |
| `energy_total_kwh` | decimal | Odometro acumulado de energia. |
| `is_on` | boolean | Estado del enchufe. |

Clave primaria conceptual:

```text
(time, device_id)
```

La aplicacion calcula costes usando diferencias entre lecturas consecutivas de `energy_total_kwh`, no sumando directamente `power_w`. Esto es mas estable cuando el dispositivo proporciona un contador acumulado.

## 5. Tabla `devices`

| Columna | Uso |
| --- | --- |
| `id` | Identificador interno. |
| `mac_address` | Identificador unico del enchufe o simulador. |
| `user_id` | Propietario del dispositivo. |
| `name` | Nombre mostrado en UI. |
| `is_on` | Estado logico actual. |
| `is_simulated` | Indica si lo procesa el job de simulacion. |
| `simulation_profile` | Perfil de consumo sintetico. |

Los dispositivos simulados usan MACs generadas con prefijo `SIM`. El Shelly real usa la MAC extraida del topic MQTT.

## 6. Tablas tarifarias

### `tariffs`

Representa el contrato o plantilla energetica.

| Columna | Uso |
| --- | --- |
| `name` | Nombre de tarifa. |
| `market` | Mercado o clasificacion de contrato. |
| `access_tariff_code` | Peaje: `2.0TD`, `3.0TD`, `6.1TD`, `6.2TD`. |
| `geographic_zone` | `PENINSULA`, `CANARIAS`, `ISLAS_BALEARES`, `CEUTA`, `MELILLA`. |
| `energy_company` | Comercializadora o etiqueta. |

`tariffs-td-schema.sql` anade checks para limitar codigos validos.

### `periods`

Precio por periodo.

| Columna | Uso |
| --- | --- |
| `tariff_id` | Tarifa asociada. |
| `period_code` | `P1` a `P6`. |
| `price_kwh` | Precio de energia en euros/kWh. |

Indice:

```sql
CREATE UNIQUE INDEX IF NOT EXISTS ux_periods_tariff_period_code
  ON periods (tariff_id, period_code);
```

### `tariff_contracted_powers`

Potencia contratada por periodo.

| Columna | Uso |
| --- | --- |
| `tariff_id` | Tarifa asociada. |
| `period_code` | Periodo P1-P6. |
| `contracted_power_kw` | Umbral de potencia en kW. |

Se usa para alertas de maximetro.

### `tariff_calendar_slots`

Tabla de calendario regulatorio. Relaciona mes, zona, tipo de dia y tramo horario con un periodo.

| Columna | Uso |
| --- | --- |
| `access_tariff_code` | Peaje al que aplica el slot. |
| `geographic_zone` | Zona geografica. |
| `month_number` | Mes 1-12. |
| `season_code` | `HIGH`, `MID_HIGH`, `MID`, `LOW`. |
| `day_type` | `A`, `B`, `B1`, `C`, `D`. |
| `period_code` | Periodo resultante. |
| `start_time`, `end_time` | Intervalo local. |

Indice de busqueda:

```sql
CREATE UNIQUE INDEX IF NOT EXISTS ux_tariff_calendar_slots_lookup
  ON tariff_calendar_slots (
    access_tariff_code, geographic_zone, month_number, day_type, start_time, end_time
  );
```

El seed cubre `2.0TD` y `3.0TD` para `PENINSULA` e `ISLAS_BALEARES`, con 336 filas.

## 7. Tabla `alerts`

| Columna | Uso |
| --- | --- |
| `id` | Identificador de alerta. |
| `user_id` | Usuario afectado. |
| `device_id` | Dispositivo que genero la alerta. |
| `type` | Actualmente `OVERPOWER`. |
| `message` | Mensaje mostrado al usuario. |
| `created_at` | Fecha de creacion heredada de auditoria. |

La alerta se crea desde backend, no desde frontend, para que el usuario no pueda falsear incidencias.

## 8. Consultas reales sobre `readings`

Repositorio: `ReadingRepository`

### Ultima lectura de un dispositivo

```java
Optional<Reading> findFirstByDeviceMacAddressOrderByTimeDesc(String macAddress);
```

Uso:

- Obtener lectura actual.
- Calcular odometro siguiente en simulacion.

### Lecturas por intervalo

```java
@Query("SELECT r FROM Reading r WHERE r.device.macAddress = :macAddress " +
       "AND r.time >= :start AND r.time <= :end ORDER BY r.time ASC")
List<Reading> findReadingsInInterval(String macAddress, Instant start, Instant end);
```

Uso:

- `ConsumptionService.calculateCostInPeriod`.
- `ConsumptionService.calculateGhostCost`.
- Historial reciente del dashboard.

La ordenacion ascendente es obligatoria porque el coste se calcula comparando lectura actual contra lectura anterior.

### Busqueda por clave compuesta funcional

```java
Optional<Reading> findByTimeAndDeviceMacAddress(Instant time, String macAddress);
```

Uso:

- Endpoint `GET /api/v1/readings/search`.

### Borrado puntual

```java
@Query("DELETE FROM Reading r WHERE r.time = :time AND r.device.macAddress = :macAddress")
Long deleteByTimeAndDeviceMacAddress(Instant time, String macAddress);
```

Uso:

- Endpoint `DELETE /api/v1/readings/search`.

### Borrado por dispositivo

```java
@Query("DELETE FROM Reading r WHERE r.device.macAddress = :macAddress")
int deleteAllByDeviceMacAddress(String macAddress);
```

Uso:

- `DeviceService.deleteById`, antes de borrar el dispositivo.

## 9. Consultas de calendario tarifario

Repositorio: `TariffCalendarSlotRepository`

### Resolver periodo por hora local

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

Motivo de la consulta:

- El precio no se puede saber solo con la hora.
- Hace falta peaje, zona, mes, tipo de dia y tramo.
- El caso `startTime = endTime` cubre dias completos tipo `D` para P6.
- `endOfDay` resuelve el problema practico de representar el cierre del dia con `23:59`.

### Resolver tipo de dia laborable

```java
@Query("""
  SELECT DISTINCT cs.dayType FROM TariffCalendarSlot cs
  WHERE cs.accessTariffCode = :accessTariffCode
    AND cs.geographicZone   = :zone
    AND cs.monthNumber      = :month
    AND cs.dayType          <> 'D'
""")
Optional<String> findWorkdayType(...);
```

Esta consulta permite deducir si un dia laborable del mes cae en temporada A, B, B1 o C.

## 10. Algoritmo de coste energetico

Servicio: `ConsumptionService.calculateCostInPeriod`

Pasos:

1. Carga lecturas de la MAC entre `start` y `end`.
2. Si hay menos de dos lecturas, devuelve `0`.
3. Comprueba que el dispositivo tenga usuario y tarifa.
4. Recorre desde la segunda lectura.
5. Calcula:

```text
delta = current.energyTotalKwh - previous.energyTotalKwh
```

6. Descarta delta nulo, negativo o con campos nulos.
7. Resuelve periodo tarifario del instante actual.
8. Suma:

```text
coste_tramo = delta_kWh * priceKwh_del_periodo
```

9. Devuelve total con 2 decimales.

Esta logica evita contar reinicios de odometro como consumo negativo o corrupto.

## 11. Algoritmo de consumo fantasma

Servicio: `ConsumptionService.calculateGhostCost`

Es igual al coste general, pero filtra por ventana nocturna:

```java
int hour = instant.atZone(zoneId).getHour();
return hour >= 0 && hour < 6;
```

La zona horaria viene de `CalendarResolverService`:

- `CANARIAS` -> `Atlantic/Canary`.
- Resto -> `Europe/Madrid`.

Esta decision es importante porque un `Instant` UTC puede caer en distinto dia u hora local segun la zona del contrato.

## 12. Uso actual de TimescaleDB

La aplicacion aprovecha TimescaleDB principalmente para particionar `readings` como serie temporal. En el codigo actual no se usan todavia:

- `time_bucket`.
- Agregados continuos.
- Compresion.
- Politicas de retencion.
- SQL nativo analitico sobre hypertables.

La analitica se realiza en Java con JPQL/JPA. La razon es que el calculo tarifario depende de reglas de negocio complejas: calendario CNMC, zonas horarias, deltas del odometro y potencia contratada por periodo.

## 13. Consultas TimescaleDB recomendadas para explotacion futura

Estas consultas no sustituyen al codigo actual, pero son coherentes con el modelo y servirian como evolucion.

### Potencia media por bloques de 15 minutos

```sql
SELECT
  time_bucket('15 minutes', r.time) AS bucket,
  d.mac_address,
  AVG(r.power_w) AS avg_power_w
FROM readings r
JOIN devices d ON d.id = r.device_id
WHERE d.mac_address = :macAddress
  AND r.time BETWEEN :start AND :end
GROUP BY bucket, d.mac_address
ORDER BY bucket;
```

### Energia consumida por hora usando odometro

```sql
SELECT
  time_bucket('1 hour', r.time) AS hour,
  d.mac_address,
  MAX(r.energy_total_kwh) - MIN(r.energy_total_kwh) AS consumed_kwh
FROM readings r
JOIN devices d ON d.id = r.device_id
WHERE d.mac_address = :macAddress
  AND r.time BETWEEN :start AND :end
GROUP BY hour, d.mac_address
ORDER BY hour;
```

### Deteccion SQL de posibles picos

```sql
SELECT
  r.time,
  d.mac_address,
  r.power_w
FROM readings r
JOIN devices d ON d.id = r.device_id
WHERE r.power_w > :thresholdWatts
  AND r.time BETWEEN :start AND :end
ORDER BY r.time DESC;
```

Estas consultas encajan con TimescaleDB porque agrupan por tiempo y reducen volumen antes de llegar al backend.

## 14. Limitaciones y mejoras

- `ReadingRepository` declara `JpaRepository<Reading, Long>` aunque la entidad usa `ReadingId` como clave compuesta; funcionalmente se usan metodos por campos, pero el generico podria ajustarse.
- La aplicacion carga lecturas raw en memoria para calcular costes. Para rangos grandes convendria paginar, preagregar o mover parte del calculo a SQL.
- El seed regulatorio cubre una parte concreta: `2.0TD` y `3.0TD` para Peninsula e Islas Baleares.
- TimescaleDB esta preparado, pero todavia no se explotan sus caracteristicas avanzadas.

# Anexo D. TimescaleDB, modelo relacional y consultas analíticas

Wattimizer usa PostgreSQL como base relacional y TimescaleDB para optimizar la tabla temporal `readings`. La mayor parte del modelo sigue siendo relacional clásico; la parte de series temporales se concentra en las lecturas IoT.

## D.1. Estructura de tablas

### `users`

Entidad: `UserEntity`.

| Campo | Origen | Descripción |
|---|---|---|
| `id` | `BaseEntity` | PK autoincremental. |
| `tariff_id` | `@ManyToOne Tariff` | Tarifa activa del usuario, nullable. |
| `username` | columna única | Email usado como identificador. |
| `password` | columna obligatoria | Contraseña cifrada. |
| `role` | enum `Role` | `ROLE_USER` o `ROLE_ADMIN`. |
| `active` | boolean | Estado de cuenta. |
| `created_at`, `updated_at`, `created_by`, `updated_by` | `BaseEntity` | Auditoría JPA. |

### `federated_identities`

Entidad: `FederatedIdentity`.

| Campo | Descripción |
|---|---|
| `id` | PK. |
| `user_id` | FK obligatoria a `users`. |
| `provider` | Proveedor OAuth2. |
| `provider_subject` | Identificador único en el proveedor. |
| `email_at_login` | Email recibido durante login. |
| `created_at` | Fecha de vinculación. |

Tiene restricción única `(provider, provider_subject)`.

### `devices`

Entidad: `Device`.

| Campo | Descripción |
|---|---|
| `id` | PK heredada. |
| `user_id` | FK nullable a `users`. |
| `name` | Nombre visible del medidor. |
| `mac_address` | MAC única. |
| `is_on` | Estado lógico. |
| `is_simulated` | Indica si el dispositivo es simulado. |
| `simulation_profile` | Perfil usado por el simulador. |
| auditoría | Campos heredados de `BaseEntity`. |

La MAC es la clave funcional que conecta MQTT, REST y Angular.

### `readings`

Entidad: `Reading`.

| Campo | Descripción |
|---|---|
| `time` | Parte temporal de la PK compuesta. |
| `device_id` | Parte de la PK compuesta y FK a `devices`. |
| `power_w` | Potencia activa en vatios. |
| `energy_total_kwh` | Energía acumulada en kWh. |
| `is_on` | Estado del equipo en la lectura. |

La clave compuesta se modela con `ReadingId`, formado por `device` y `time`.

### `alerts`

Entidad: `Alert`.

| Campo | Descripción |
|---|---|
| `id` | PK heredada. |
| `user_id` | FK obligatoria a `users`. |
| `device_id` | FK obligatoria a `devices`. |
| `type` | Tipo de alerta, actualmente `OVERPOWER`. |
| `message` | Texto generado para el usuario. |
| auditoría | Permite obtener `createdAt` en el DTO. |

### `tariffs`

Entidad: `Tariff`.

| Campo | Descripción |
|---|---|
| `id` | PK heredada. |
| `name` | Nombre de tarifa. |
| `market` | Mercado libre o regulado. |
| `access_tariff_code` | Peaje: `2.0TD`, `3.0TD`, `6.1TD`, `6.2TD`. |
| `geographic_zone` | Zona: `PENINSULA`, `CANARIAS`, `ISLAS_BALEARES`, `CEUTA`, `MELILLA`. |
| `energy_company` | Comercializadora. |

Las tarifas pueden ser plantillas de catálogo o clones privados asignados a usuarios.

### `periods`

Entidad: `Period`.

| Campo | Descripción |
|---|---|
| `id` | PK. |
| `tariff_id` | FK obligatoria a `tariffs`. |
| `period_code` | `P1` a `P6`. |
| `price_kwh` | Precio contractual en €/kWh. |

Restricción única: `(tariff_id, period_code)`.

### `tariff_contracted_powers`

Entidad: `TariffContractedPower`.

| Campo | Descripción |
|---|---|
| `id` | PK. |
| `tariff_id` | FK obligatoria a `tariffs`. |
| `period_code` | Periodo `P1` a `P6`. |
| `contracted_power_kw` | Potencia contratada en kW. |

Restricción única: `(tariff_id, period_code)`.

### `tariff_calendar_slots`

Entidad: `TariffCalendarSlot`.

| Campo | Descripción |
|---|---|
| `id` | PK. |
| `access_tariff_code` | Peaje al que aplica el slot. |
| `geographic_zone` | Zona regulatoria. |
| `month_number` | Mes 1-12. |
| `season_code` | Temporada: `HIGH`, `MID_HIGH`, `MID`, `LOW`. |
| `day_type` | Tipo de día: `A`, `B`, `B1`, `C`, `D`. |
| `period_code` | Periodo resultante. |
| `start_time` | Hora local de inicio. |
| `end_time` | Hora local de fin. |

Esta tabla separa calendario regulatorio de precios contractuales. Así, `periods` guarda precios y `tariff_calendar_slots` decide qué periodo aplica en cada momento.

## D.2. Conversión de `readings` en hypertable

Script: `backend/src/main/resources/db/dev-seed/01-hypertable.sql`.

```sql
SELECT create_hypertable('readings', 'time');
```

Debe ejecutarse después de que Hibernate cree la tabla y antes de que entren datos MQTT. Si la tabla ya contiene filas, el propio script indica la variante:

```sql
SELECT create_hypertable('readings', 'time', migrate_data => true);
```

Verificación:

```sql
SELECT hypertable_name, num_chunks
FROM timescaledb_information.hypertables;
```

Resultado esperado:

```text
readings | 0
```

o un número de chunks mayor que cero si ya existen lecturas.

## D.3. Scripts complementarios de esquema

`tariffs-td-schema.sql` añade restricciones que Hibernate no expresa completamente:

- `chk_tariffs_access_tariff_code`
- `chk_tariffs_geographic_zone`
- `chk_periods_period_code`
- `chk_tariff_contracted_powers_period_code`
- `chk_tariff_contracted_powers_positive`
- `chk_tariff_calendar_slots_access_tariff_code`
- `chk_tariff_calendar_slots_geographic_zone`
- `chk_tariff_calendar_slots_month_number`
- `chk_tariff_calendar_slots_season_code`
- `chk_tariff_calendar_slots_day_type`
- `chk_tariff_calendar_slots_period_code`

También crea índices:

```sql
CREATE UNIQUE INDEX IF NOT EXISTS ux_periods_tariff_period_code
  ON periods (tariff_id, period_code);

CREATE INDEX IF NOT EXISTS ix_tariff_contracted_powers_tariff_id
  ON tariff_contracted_powers (tariff_id);

CREATE UNIQUE INDEX IF NOT EXISTS ux_tariff_calendar_slots_lookup
  ON tariff_calendar_slots (
    access_tariff_code, geographic_zone, month_number, day_type, start_time, end_time
  );

CREATE INDEX IF NOT EXISTS ix_tariff_calendar_slots_period_code
  ON tariff_calendar_slots (access_tariff_code, geographic_zone, month_number, day_type, period_code);
```

## D.4. Seed regulatorio de calendario

`seed-tariff-calendar-slots.sql` carga la tabla `tariff_calendar_slots`.

Cobertura actual:

| Peajes | Zonas | Total |
|---|---|---:|
| `2.0TD`, `3.0TD` | `PENINSULA`, `ISLAS_BALEARES` | 336 filas |

Aunque las entidades y checks admiten más zonas y peajes, el seed actual no cubre por completo `CANARIAS`, `CEUTA`, `MELILLA`, `6.1TD` ni `6.2TD`. Si se usan esas combinaciones sin ampliar el seed, `CalendarResolverService` puede no resolver periodo y los servicios devolverán coste cero de forma degradada.

## D.5. Consultas de repositorio

### Última lectura por dispositivo

Repositorio:

```java
Optional<Reading> findFirstByDeviceMacAddressOrderByTimeDesc(String macAddress);
```

SQL equivalente:

```sql
SELECT r.*
FROM readings r
JOIN devices d ON d.id = r.device_id
WHERE d.mac_address = :macAddress
ORDER BY r.time DESC
LIMIT 1;
```

Uso:

- `ReadingService.findByDevice()`.
- Cálculo del siguiente odómetro en simulación.

### Buscar una lectura por clave compuesta

Repositorio:

```java
Optional<Reading> findByTimeAndDeviceMacAddress(Instant time, String macAddress);
```

SQL equivalente:

```sql
SELECT r.*
FROM readings r
JOIN devices d ON d.id = r.device_id
WHERE r.time = :time
  AND d.mac_address = :macAddress;
```

Uso:

- Endpoint `GET /api/v1/readings/search`.
- Validación previa al borrado de una lectura concreta.

### Lecturas de un intervalo

Repositorio:

```java
@Query("""
SELECT r FROM Reading r
WHERE r.device.macAddress = :macAddress
  AND r.time >= :start
  AND r.time <= :end
ORDER BY r.time ASC
""")
List<Reading> findReadingsInInterval(...)
```

SQL equivalente:

```sql
SELECT r.*
FROM readings r
JOIN devices d ON d.id = r.device_id
WHERE d.mac_address = :macAddress
  AND r.time >= :start
  AND r.time <= :end
ORDER BY r.time ASC;
```

Uso:

- Histórico reciente del dashboard.
- Cálculo de coste total.
- Cálculo de consumo fantasma.

### Borrado por lectura

Repositorio:

```java
DELETE FROM Reading r
WHERE r.time = :time
  AND r.device.macAddress = :macAddress
```

SQL equivalente:

```sql
DELETE FROM readings r
USING devices d
WHERE r.device_id = d.id
  AND r.time = :time
  AND d.mac_address = :macAddress;
```

### Borrado de lecturas de un dispositivo

Repositorio:

```java
DELETE FROM Reading r
WHERE r.device.macAddress = :macAddress
```

SQL equivalente:

```sql
DELETE FROM readings r
USING devices d
WHERE r.device_id = d.id
  AND d.mac_address = :macAddress;
```

Uso:

- `DeviceService.deleteById()` antes de borrar el dispositivo.

## D.6. Resolución de periodo tarifario

`CalendarResolverService.resolveApplicablePeriod()` resuelve el precio aplicable en cuatro pasos:

1. Lee `accessTariffCode` y `geographicZone` de la tarifa.
2. Convierte el `Instant` a hora local:
   - `CANARIAS` usa `Atlantic/Canary`.
   - El resto usa `Europe/Madrid`.
3. Resuelve el tipo de día:
   - Sábado y domingo son `D`.
   - Laborable usa `findWorkdayType(accessTariffCode, zone, month)`.
4. Busca el `period_code` en `tariff_calendar_slots`.
5. Busca el precio en `periods` con `findByTariffIdAndPeriodCode(tariffId, periodCode)`.

Consulta JPQL principal:

```java
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
```

SQL equivalente:

```sql
SELECT cs.period_code
FROM tariff_calendar_slots cs
WHERE cs.access_tariff_code = :accessTariffCode
  AND cs.geographic_zone = :zone
  AND cs.month_number = :month
  AND cs.day_type = :dayType
  AND (
    (cs.start_time <> cs.end_time
      AND cs.start_time <= :localTime
      AND cs.end_time > :localTime)
    OR (cs.start_time = cs.end_time AND cs.day_type = 'D')
    OR (cs.end_time = TIME '23:59' AND :localTime >= cs.start_time)
  );
```

## D.7. Consulta analítica: coste energético acumulado

Código: `ConsumptionService.calculateCostInPeriod`.

Datos de entrada:

- `macAddress`
- `start`
- `end`

Proceso:

1. Obtiene lecturas ordenadas por tiempo.
2. Si hay menos de dos lecturas, devuelve `0`.
3. Recupera dispositivo, usuario y tarifa.
4. Recorre pares consecutivos.
5. Calcula delta positivo de `energyTotalKwh`.
6. Resuelve periodo aplicable para el instante de la lectura actual.
7. Multiplica delta por `priceKwh`.
8. Redondea a dos decimales.

Pseudocódigo:

```text
total = 0
for each (previous, current) in readings consecutive:
    delta = current.energyTotalKwh - previous.energyTotalKwh
    if delta <= 0:
        continue
    period = resolveApplicablePeriod(tariff, current.time)
    if period exists:
        total += delta * period.priceKwh
return round(total, 2)
```

La decisión de usar deltas positivos evita sumar consumos falsos si el dispositivo reinicia su contador acumulado.

## D.8. Consulta analítica: consumo fantasma

Código: `ConsumptionService.calculateGhostCost`.

Es similar al cálculo de coste total, pero solo suma tramos cuya lectura actual cae entre las 00:00 y las 05:59 en la zona local del contrato.

```java
private boolean isGhostWindow(Tariff tariff, Instant instant) {
    ZoneId zoneId = calendarResolverService.resolveZoneIdForTariff(tariff);
    int hour = instant.atZone(zoneId).getHour();
    return hour >= 0 && hour < 6;
}
```

No se confunde esta ventana con el periodo regulatorio valle. La lógica del código documenta que "consumo fantasma" es una definición funcional de inactividad nocturna.

## D.9. Consulta analítica: alerta de maxímetro

Código: `AlertService.checkPowerThreshold`.

Proceso:

1. Valida que la lectura tenga dispositivo, potencia y tiempo.
2. Recupera usuario y tarifa del dispositivo.
3. Resuelve periodo aplicable con `CalendarResolverService`.
4. Busca potencia contratada:

```java
findByTariffIdAndPeriodCode(tariff.getId(), periodCode)
```

5. Convierte `powerW` a kW:

```java
currentPowerKw = powerW / 1000
```

6. Si `currentPowerKw > contractedPowerKw`, crea alerta `OVERPOWER`.

SQL equivalente para localizar potencia contratada:

```sql
SELECT *
FROM tariff_contracted_powers
WHERE tariff_id = :tariffId
  AND period_code = :periodCode;
```

## D.10. Consultas útiles para explotación

Estas consultas no aparecen como SQL directo en el backend, pero son coherentes con las tablas actuales y sirven para validar producción.

### Últimas lecturas por MAC

```sql
SELECT r.time, d.mac_address, r.power_w, r.energy_total_kwh, r.is_on
FROM readings r
JOIN devices d ON d.id = r.device_id
WHERE d.mac_address = '9070694d3590'
ORDER BY r.time DESC
LIMIT 20;
```

### Dispositivos simulados activos

```sql
SELECT id, name, mac_address, simulation_profile, is_on
FROM devices
WHERE is_simulated = true
ORDER BY id;
```

### Alertas recientes por usuario

```sql
SELECT a.created_at, u.username, d.mac_address, a.type, a.message
FROM alerts a
JOIN users u ON u.id = a.user_id
JOIN devices d ON d.id = a.device_id
ORDER BY a.created_at DESC
LIMIT 50;
```

### Verificación de cobertura de calendario

```sql
SELECT access_tariff_code, geographic_zone, COUNT(*) AS slots
FROM tariff_calendar_slots
GROUP BY access_tariff_code, geographic_zone
ORDER BY access_tariff_code, geographic_zone;
```

## D.11. Limitaciones actuales de TimescaleDB

Implementado:

- Extensión TimescaleDB activable por script.
- Conversión de `readings` a hypertable particionada por `time`.
- Consultas por intervalo temporal desde JPA.

No implementado todavía:

- `time_bucket` para agregaciones por minuto, hora o día.
- Continuous aggregates.
- Políticas de compresión.
- Políticas de retención automática.
- Índices específicos adicionales sobre `readings(device_id, time DESC)`.

Estas mejoras son adecuadas para una fase posterior, cuando el volumen de lecturas aumente y el dashboard necesite históricos largos sin cargar todos los puntos en memoria.

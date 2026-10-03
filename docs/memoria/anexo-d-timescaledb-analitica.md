# Anexo D - TimescaleDB y consultas analiticas

## 1. Papel de la base de datos

Wattimizer usa PostgreSQL como base relacional y TimescaleDB para tratar las lecturas electricas como serie temporal. La informacion de usuarios, dispositivos, tarifas y alertas encaja en tablas relacionales normales; en cambio, `readings` crece continuamente con el tiempo y se consulta por rangos temporales, por eso se convierte en hypertable.

## 2. Modelo de tablas

### 2.1. `users`

Entidad: `UserEntity`.

| Columna | Tipo aproximado | Notas |
|---|---|---|
| `id` | bigint | PK heredada de `BaseEntity`. |
| `username` | varchar | Unico y obligatorio; se usa como email. |
| `password` | varchar | Hash generado con `PasswordEncoder`. |
| `role` | varchar | `ROLE_USER` o `ROLE_ADMIN`. |
| `active` | boolean | Controla si la cuenta esta habilitada. |
| `tariff_id` | bigint | FK opcional a `tariffs`. |
| `created_at`, `updated_at` | timestamp | Auditoria automatica. |
| `created_by`, `updated_by` | varchar | Auditoria de Spring Data. |

La tarifa del usuario puede ser nula. Esto permite crear administradores o usuarios que aun no han configurado contrato electrico.

### 2.2. `federated_identities`

Entidad: `FederatedIdentity`.

| Columna | Notas |
|---|---|
| `id` | PK. |
| `user_id` | FK obligatoria a `users`. |
| `provider` | `GOOGLE` o `GITHUB`. |
| `provider_subject` | Identificador unico del usuario en el proveedor. |
| `email_at_login` | Email recibido durante el login. |
| `created_at` | Fecha de vinculacion. |

Restriccion importante:

```text
UNIQUE(provider, provider_subject)
```

Esto evita confundir usuarios de distintos proveedores aunque coincidan emails.

### 2.3. `devices`

Entidad: `Device`.

| Columna | Notas |
|---|---|
| `id` | PK. |
| `user_id` | FK opcional a `users`; puede estar libre o asignado a `SYSTEM`. |
| `name` | Nombre visible. |
| `mac_address` | Unico y obligatorio. |
| `is_on` | Estado logico del equipo. |
| `is_simulated` | `true` para dispositivos virtuales. |
| `simulation_profile` | Perfil usado por el generador de telemetria. |

Las MAC simuladas se generan con prefijo `SIM`, por ejemplo `SIM000000001`.

### 2.4. `readings`

Entidad: `Reading`.

| Columna | Notas |
|---|---|
| `device_id` | Parte de la PK y FK a `devices`. |
| `time` | Parte de la PK; instante UTC de la lectura. |
| `power_w` | Potencia activa instantanea en W. |
| `energy_total_kwh` | Energia acumulada en kWh. |
| `is_on` | Estado del dispositivo si el payload lo aporta. |

Clave primaria conceptual:

```text
(device_id, time)
```

La combinacion evita duplicar lecturas del mismo dispositivo en el mismo instante.

### 2.5. `alerts`

Entidad: `Alert`.

| Columna | Notas |
|---|---|
| `id` | PK. |
| `user_id` | Usuario que recibe la alerta. |
| `device_id` | Dispositivo que provoco la alerta. |
| `type` | Actualmente `OVERPOWER`. |
| `message` | Texto explicativo. |
| `created_at`, `updated_at` | Auditoria. |

### 2.6. `tariffs`

Entidad: `Tariff`.

| Columna | Notas |
|---|---|
| `id` | PK. |
| `name` | Nombre comercial o interno. |
| `market` | Mercado libre o regulado. |
| `access_tariff_code` | `2.0TD`, `3.0TD`, `6.1TD` o `6.2TD`. |
| `geographic_zone` | `PENINSULA`, `CANARIAS`, `ISLAS_BALEARES`, `CEUTA`, `MELILLA`. |
| `energy_company` | Comercializadora. |

Una tarifa puede ser plantilla del catalogo o contrato privado clonado para un usuario.

### 2.7. `periods`

Entidad: `Period`.

| Columna | Notas |
|---|---|
| `id` | PK. |
| `tariff_id` | FK obligatoria a `tariffs`. |
| `period_code` | `P1` a `P6`. |
| `price_kwh` | Precio en euros/kWh. |

Restriccion:

```text
UNIQUE(tariff_id, period_code)
```

Los horarios no estan aqui. Esta tabla solo guarda precios contractuales.

### 2.8. `tariff_contracted_powers`

Entidad: `TariffContractedPower`.

| Columna | Notas |
|---|---|
| `id` | PK. |
| `tariff_id` | FK obligatoria a `tariffs`. |
| `period_code` | `P1` a `P6`. |
| `contracted_power_kw` | Potencia contratada en kW. |

Tambien usa unicidad por `(tariff_id, period_code)`.

### 2.9. `tariff_calendar_slots`

Entidad: `TariffCalendarSlot`.

| Columna | Notas |
|---|---|
| `id` | PK. |
| `access_tariff_code` | Peaje de acceso. |
| `geographic_zone` | Zona geografica. |
| `month_number` | Mes `1-12`. |
| `season_code` | `HIGH`, `MID_HIGH`, `MID`, `LOW`. |
| `day_type` | `A`, `B`, `B1`, `C`, `D`. |
| `period_code` | Periodo aplicable. |
| `start_time` | Inicio del tramo horario. |
| `end_time` | Fin del tramo horario. |

Esta tabla es una dimension regulatoria. Permite resolver:

```text
peaje + zona + mes + tipo de dia + hora local -> period_code
```

## 3. Hypertable de TimescaleDB

Script: `backend/src/main/resources/db/dev-seed/01-hypertable.sql`.

```sql
SELECT create_hypertable('readings', 'time');
```

El script debe ejecutarse despues de que Hibernate cree la tabla `readings` y antes de recibir datos MQTT. Si ya hubiera filas, el comentario del script indica usar `migrate_data => true`.

Consulta de verificacion:

```sql
SELECT hypertable_name, num_chunks
FROM timescaledb_information.hypertables;
```

Resultado esperado al inicio:

```text
readings | 0
```

## 4. Scripts SQL del proyecto

| Script | Funcion |
|---|---|
| `00-extensions.sql` | Activa extensiones necesarias, incluida TimescaleDB. |
| `01-hypertable.sql` | Convierte `readings` en hypertable. |
| `03-seed-users-dev.sql` | Crea usuarios de desarrollo. |
| `04-seed-device-shelly.sql` | Inserta dispositivo Shelly fisico de prueba. |
| `05-seed-device-simulation.sql` | Crea dispositivos simulados por perfil. |
| `tariffs-td-schema.sql` | Ajusta constraints e indices del modelo tarifario TD. |
| `seed-tariff-calendar-slots.sql` | Carga calendario regulatorio para `2.0TD` y `3.0TD` en Peninsula e Islas Baleares. |
| `prod/99-resync-sequences.sql` | Reajusta secuencias en produccion tras cargas manuales. |

## 5. Consultas JPA reales

### 5.1. Lecturas por intervalo

Repositorio: `ReadingRepository`.

```java
@Query("SELECT r FROM Reading r WHERE r.device.macAddress = :macAddress " +
       "AND r.time >= :start AND r.time <= :end ORDER BY r.time ASC")
List<Reading> findReadingsInInterval(
    @Param("macAddress") String macAddress,
    @Param("start") Instant start,
    @Param("end") Instant end);
```

Uso:

- dashboard, para historico reciente;
- calculo de coste;
- calculo de consumo fantasma.

Equivalente SQL:

```sql
SELECT r.*
FROM readings r
JOIN devices d ON d.id = r.device_id
WHERE d.mac_address = :macAddress
  AND r.time >= :start
  AND r.time <= :end
ORDER BY r.time ASC;
```

TimescaleDB optimiza especialmente esta consulta porque filtra por la columna temporal de la hypertable.

### 5.2. Ultima lectura de un dispositivo

```java
Optional<Reading> findFirstByDeviceMacAddressOrderByTimeDesc(String macAddress);
```

Uso:

- obtener ultima lectura por API;
- calcular el siguiente odometro de energia en la simulacion.

Equivalente:

```sql
SELECT r.*
FROM readings r
JOIN devices d ON d.id = r.device_id
WHERE d.mac_address = :macAddress
ORDER BY r.time DESC
LIMIT 1;
```

### 5.3. Busqueda por clave compuesta

```java
Optional<Reading> findByTimeAndDeviceMacAddress(Instant time, String macAddress);
```

Equivalente:

```sql
SELECT r.*
FROM readings r
JOIN devices d ON d.id = r.device_id
WHERE r.time = :time
  AND d.mac_address = :macAddress;
```

### 5.4. Borrado de lecturas de un dispositivo

```java
@Modifying
@Transactional
@Query("DELETE FROM Reading r WHERE r.device.macAddress = :macAddress")
int deleteAllByDeviceMacAddress(@Param("macAddress") String macAddress);
```

Se ejecuta antes de borrar un dispositivo para mantener integridad referencial.

## 6. Resolucion del periodo tarifario

Servicio: `CalendarResolverService`.

Algoritmo:

1. lee `accessTariffCode` y `geographicZone` de la tarifa;
2. convierte el `Instant` a hora local:
   - `CANARIAS` -> `Atlantic/Canary`;
   - resto -> `Europe/Madrid`;
3. obtiene mes y hora local;
4. determina tipo de dia:
   - sabado/domingo -> `D`;
   - laborable -> consulta `tariff_calendar_slots`;
5. busca `period_code`;
6. busca el `Period` del contrato con ese codigo.

Consulta de calendario:

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

La clausula final con `endOfDay` existe porque PostgreSQL `TIME` no representa `24:00`, asi que el seed usa `23:59` para el ultimo tramo diario.

## 7. Calculo de coste energetico

Servicio: `ConsumptionService.calculateCostInPeriod`.

Entrada:

- `macAddress`;
- `start`;
- `end`.

Flujo:

1. carga lecturas ordenadas por tiempo;
2. si hay menos de dos lecturas, devuelve `0`;
3. busca dispositivo, usuario y tarifa;
4. recorre lecturas desde la segunda;
5. calcula delta positivo:

```java
BigDecimal delta = current.getEnergyTotalKwh().subtract(previous.getEnergyTotalKwh());
```

6. descarta deltas nulos o no positivos, porque pueden indicar reset del contador;
7. resuelve el periodo aplicable al instante de la lectura actual;
8. multiplica `deltaKwh * priceKwh`;
9. suma y redondea a dos decimales.

Pseudocodigo:

```text
total = 0
for lectura_actual desde posicion 1:
    delta = actual.energy_total_kwh - anterior.energy_total_kwh
    si delta <= 0: continuar
    periodo = resolver_periodo(tarifa, actual.time)
    si no hay periodo: continuar
    total += delta * periodo.price_kwh
return total redondeado a 2 decimales
```

La decision de usar deltas de energia acumulada es mas robusta que integrar potencia instantanea, porque aprovecha el contador energetico del dispositivo.

## 8. Calculo de consumo fantasma

Metodo: `ConsumptionService.calculateGhostCost`.

Es similar al coste normal, pero solo suma deltas cuyo instante cae en la ventana:

```text
00:00 <= hora local < 06:00
```

Codigo relevante:

```java
private boolean isGhostWindow(Tariff tariff, Instant instant) {
    ZoneId zoneId = calendarResolverService.resolveZoneIdForTariff(tariff);
    int hour = instant.atZone(zoneId).getHour();
    return hour >= 0 && hour < 6;
}
```

Esta ventana representa inactividad nocturna de negocio, no necesariamente periodo valle legal.

## 9. Calculo instantaneo auxiliar

Hay un metodo auxiliar en `ConsumptionService` y otro equivalente historico en `TariffService`:

```java
calculateInstantaneousCost(String macAddress, double powerW, int durationSeconds)
```

Formula:

```text
kWh = (powerW / 1000) * (durationSeconds / 3600)
coste = kWh * priceKwh
```

Este calculo sirve para estimaciones sobre una ventana corta, aunque los endpoints publicos de analitica usan actualmente el acumulado `energyTotalKwh`.

## 10. Alertas de maximetro y potencia contratada

Servicio: `AlertService.checkPowerThreshold`.

Proceso:

1. valida que lectura, dispositivo, potencia y tiempo no sean nulos;
2. exige usuario con tarifa asignada;
3. resuelve periodo actual;
4. busca potencia contratada para ese periodo:

```java
tariffContractedPowerRepository.findByTariffIdAndPeriodCode(tariff.getId(), periodCode);
```

5. convierte W a kW:

```java
BigDecimal currentPowerKw = reading.getPowerW()
    .divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP);
```

6. si supera `contractedPowerKw`, crea una alerta `OVERPOWER`.

El mensaje incluye dispositivo, potencia medida, periodo, potencia contratada y porcentaje sobre el limite.

## 11. Consultas analiticas recomendadas para defensa

Estas consultas no sustituyen al codigo JPA, pero ayudan a comprobar datos durante una demostracion.

### 11.1. Lecturas por minuto de un dispositivo

```sql
SELECT
  time_bucket('1 minute', r.time) AS minuto,
  d.mac_address,
  AVG(r.power_w) AS potencia_media_w,
  MAX(r.energy_total_kwh) AS energia_total_kwh
FROM readings r
JOIN devices d ON d.id = r.device_id
WHERE d.mac_address = 'SIM000000001'
  AND r.time >= NOW() - INTERVAL '1 hour'
GROUP BY minuto, d.mac_address
ORDER BY minuto;
```

Sirve para ensenar la ventaja de TimescaleDB: agrupar lecturas por ventanas temporales.

### 11.2. Consumo por dispositivo en un intervalo

```sql
WITH ordered AS (
  SELECT
    d.mac_address,
    r.time,
    r.energy_total_kwh,
    LAG(r.energy_total_kwh) OVER (
      PARTITION BY d.mac_address
      ORDER BY r.time
    ) AS previous_kwh
  FROM readings r
  JOIN devices d ON d.id = r.device_id
  WHERE r.time >= :start
    AND r.time <= :end
)
SELECT
  mac_address,
  SUM(
    CASE
      WHEN previous_kwh IS NULL THEN 0
      WHEN energy_total_kwh - previous_kwh > 0 THEN energy_total_kwh - previous_kwh
      ELSE 0
    END
  ) AS consumed_kwh
FROM ordered
GROUP BY mac_address
ORDER BY consumed_kwh DESC;
```

Replica en SQL la idea que `ConsumptionService` aplica en Java: solo cuentan deltas positivos.

### 11.3. Picos de potencia

```sql
SELECT
  d.name,
  d.mac_address,
  r.time,
  r.power_w
FROM readings r
JOIN devices d ON d.id = r.device_id
WHERE r.time >= NOW() - INTERVAL '24 hours'
ORDER BY r.power_w DESC
LIMIT 10;
```

Ayuda a justificar la funcionalidad de alertas de maximetro.

### 11.4. Alertas por usuario

```sql
SELECT
  u.username,
  d.name AS device_name,
  a.type,
  a.message,
  a.created_at
FROM alerts a
JOIN users u ON u.id = a.user_id
JOIN devices d ON d.id = a.device_id
ORDER BY a.created_at DESC;
```

## 12. Consideraciones de rendimiento

- Las consultas de lecturas siempre deben filtrar por rango temporal cuando sea posible.
- Para dashboards historicos largos conviene crear agregaciones continuas de TimescaleDB.
- `readings` no debe borrarse con operaciones masivas sin planificar, porque puede contener mucho volumen.
- Los indices de `tariff_calendar_slots` estan orientados a resolver periodo por peaje, zona, mes, tipo de dia y hora.
- El backend devuelve coste `0` si no hay tarifa o calendario aplicable. Es una degradacion controlada: evita romper el dashboard cuando faltan seeds.

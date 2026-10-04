# Anexo D — TimescaleDB y analítica energética

## 1. Papel de TimescaleDB

`readings` guarda lecturas frecuentes de potencia y energía. Es una tabla de serie temporal: muchas filas ordenadas por tiempo y consultas por intervalos. Por eso el proyecto usa PostgreSQL con TimescaleDB.

Hibernate crea la tabla, pero TimescaleDB necesita un script adicional para convertirla en hypertable. Esta separación evita meter lógica específica de la base de datos dentro de las entidades JPA.

## 2. Tabla `readings`

Entidad Java: `Reading`

| Columna | Tipo lógico | Uso |
|---|---|---|
| `time` | `Instant` | Instante UTC de lectura y dimensión temporal. |
| `device_id` | FK | Dispositivo que produjo la lectura. |
| `power_w` | decimal `10,2` | Potencia activa instantánea en W. |
| `energy_total_kwh` | decimal `14,4` | Odómetro de energía acumulada. |
| `is_on` | boolean | Estado del enchufe o simulador. |

La clave se define con `@IdClass(ReadingId.class)` y combina `time` con `device`. Así dos dispositivos pueden emitir a la vez sin colisionar.

## 3. Conversión en hypertable

Script: `backend/src/main/resources/db/dev-seed/01-hypertable.sql`

```sql
SELECT create_hypertable('readings', 'time');
```

Debe ejecutarse después de activar extensiones y antes de recibir datos MQTT. Si ya hay datos, TimescaleDB requiere migración:

```sql
SELECT create_hypertable('readings', 'time', migrate_data => true);
```

Orden recomendado en una base limpia:

```sql
CREATE EXTENSION IF NOT EXISTS timescaledb;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
SELECT create_hypertable('readings', 'time');
```

`pgcrypto` no interviene en lecturas, pero se activa porque los seeds de usuarios generan hashes bcrypt en SQL.

## 4. Modelo tarifario

### 4.1. `tariffs`

| Campo | Significado |
|---|---|
| `name` | Nombre de la plantilla o contrato. |
| `market` | Mercado libre o regulado. |
| `access_tariff_code` | Peaje `2.0TD`, `3.0TD`, `6.1TD` o `6.2TD`. |
| `geographic_zone` | Zona regulatoria. |
| `energy_company` | Comercializadora. |

### 4.2. `periods`

Precio de energía por periodo.

| Campo | Significado |
|---|---|
| `tariff_id` | Tarifa propietaria. |
| `period_code` | Periodo P1-P6. |
| `price_kwh` | Precio en €/kWh. |

Existe índice único `(tariff_id, period_code)`. Por eso `TariffService.update` borra colecciones, ejecuta `entityManager.flush()` y reconstruye: evita que Hibernate inserte nuevos periodos antes de borrar los antiguos.

### 4.3. `tariff_contracted_powers`

Potencia contratada por periodo.

| Campo | Significado |
|---|---|
| `tariff_id` | Tarifa propietaria. |
| `period_code` | Periodo P1-P6. |
| `contracted_power_kw` | Límite de potencia en kW. |

`AlertService` usa esta tabla para comparar la lectura actual con el límite del periodo tarifario.

### 4.4. `tariff_calendar_slots`

Tabla de dimensión regulatoria.

| Campo | Significado |
|---|---|
| `access_tariff_code` | Peaje eléctrico. |
| `geographic_zone` | Zona española. |
| `month_number` | Mes 1-12. |
| `season_code` | Temporada `HIGH`, `MID_HIGH`, `MID`, `LOW`. |
| `day_type` | Tipo de día `A`, `B`, `B1`, `C`, `D`. |
| `period_code` | Periodo resultante. |
| `start_time`, `end_time` | Intervalo horario local. |

El seed actual cubre `PENINSULA` e `ISLAS_BALEARES` para `2.0TD` y `3.0TD`. El modelo admite más zonas y peajes, pero esos datos no están totalmente sembrados.

## 5. Consultas implementadas en repositorios

### 5.1. Última lectura

```java
Optional<Reading> findFirstByDeviceMacAddressOrderByTimeDesc(String macAddress);
```

Uso: recuperar el último odómetro antes de calcular una nueva lectura simulada.

### 5.2. Lecturas por intervalo

```java
@Query("SELECT r FROM Reading r WHERE r.device.macAddress = :macAddress " +
       "AND r.time >= :start AND r.time <= :end ORDER BY r.time ASC")
List<Reading> findReadingsInInterval(
    @Param("macAddress") String macAddress,
    @Param("start") Instant start,
    @Param("end") Instant end);
```

Al filtrar por `time`, TimescaleDB puede limitar el trabajo a los chunks del intervalo. Esta consulta alimenta histórico reciente, coste total y consumo fantasma.

### 5.3. Búsqueda por clave compuesta

```java
Optional<Reading> findByTimeAndDeviceMacAddress(Instant time, String macAddress);
```

Uso: endpoint `/api/v1/readings/search`.

### 5.4. Borrado por dispositivo

```java
@Modifying
@Query("DELETE FROM Reading r WHERE r.device.macAddress = :macAddress")
int deleteAllByDeviceMacAddress(@Param("macAddress") String macAddress);
```

Uso: `DeviceService.deleteById`, antes de borrar alertas y dispositivo.

## 6. Resolución de periodo tarifario

Clase: `CalendarResolverService`

Algoritmo:

1. Lee `accessTariffCode` y `geographicZone` de la tarifa.
2. Convierte `Instant` UTC a hora local:
   - `CANARIAS` usa `Atlantic/Canary`;
   - el resto usa `Europe/Madrid`.
3. Determina tipo de día:
   - sábado y domingo son `D`;
   - laborable se consulta en `tariff_calendar_slots`.
4. Busca `period_code` en calendario.
5. Busca el `Period` de la tarifa con ese código.

Consulta principal:

```java
SELECT cs.periodCode FROM TariffCalendarSlot cs
WHERE cs.accessTariffCode = :accessTariffCode
  AND cs.geographicZone   = :zone
  AND cs.monthNumber      = :month
  AND cs.dayType          = :dayType
  AND (
        (cs.startTime <> cs.endTime AND cs.startTime <= :localTime AND cs.endTime > :localTime)
        OR (cs.startTime = cs.endTime AND cs.dayType = 'D')
        OR (cs.endTime = :endOfDay AND :localTime >= cs.startTime)
      )
```

La condición de `endOfDay` cubre el tramo final guardado como `23:59`, ya que PostgreSQL `TIME` no utiliza `24:00`.

## 7. Cálculo de coste

Método: `ConsumptionService.calculateCostInPeriod`

Flujo:

1. Carga lecturas ordenadas por intervalo.
2. Si hay menos de dos, devuelve `0`.
3. Localiza dispositivo y tarifa.
4. Recorre pares consecutivos.
5. Calcula delta positivo de `energyTotalKwh`.
6. Resuelve periodo aplicable al instante actual.
7. Multiplica `deltaKwh * priceKwh`.
8. Suma y redondea a dos decimales.

Pseudocódigo fiel al servicio:

```java
for (int i = 1; i < readings.size(); i++) {
    Reading current = readings.get(i);
    Reading previous = readings.get(i - 1);

    Optional<BigDecimal> deltaOpt = calculatePositiveDelta(previous, current);
    if (deltaOpt.isEmpty()) continue;

    Optional<BigDecimal> costOpt = calculateStepCost(tariff, current.getTime(), deltaOpt.get());
    if (costOpt.isEmpty()) continue;

    totalCost = totalCost.add(costOpt.get());
}
```

La decisión de aceptar solo deltas positivos evita contar reinicios de dispositivo o lecturas corruptas como consumo negativo.

## 8. Consumo fantasma

Método: `ConsumptionService.calculateGhostCost`

Solo suma deltas si el instante cae entre 00:00 y 05:59 hora local del contrato:

```java
private boolean isGhostWindow(Tariff tariff, Instant instant) {
    ZoneId zoneId = calendarResolverService.resolveZoneIdForTariff(tariff);
    int hour = instant.atZone(zoneId).getHour();
    return hour >= 0 && hour < 6;
}
```

Esto es importante para Canarias: una hora UTC puede pertenecer a otro día u otra franja respecto a Madrid. `ConsumptionServiceTest` cubre esta diferencia.

## 9. Consultas SQL útiles sobre la hypertable

### 9.1. Verificar hypertable

```sql
SELECT hypertable_name, num_chunks
FROM timescaledb_information.hypertables
WHERE hypertable_name = 'readings';
```

### 9.2. Potencia media por minuto

```sql
SELECT
  time_bucket('1 minute', r.time) AS minuto,
  d.mac_address,
  AVG(r.power_w) AS potencia_media_w
FROM readings r
JOIN devices d ON d.id = r.device_id
WHERE d.mac_address = '9070694d3590'
  AND r.time >= NOW() - INTERVAL '1 hour'
GROUP BY minuto, d.mac_address
ORDER BY minuto;
```

### 9.3. Energía consumida por hora

```sql
WITH hourly AS (
  SELECT
    time_bucket('1 hour', r.time) AS hora,
    d.mac_address,
    MIN(r.energy_total_kwh) AS kwh_inicio,
    MAX(r.energy_total_kwh) AS kwh_fin
  FROM readings r
  JOIN devices d ON d.id = r.device_id
  WHERE d.mac_address = '9070694d3590'
    AND r.time >= NOW() - INTERVAL '24 hours'
  GROUP BY hora, d.mac_address
)
SELECT
  hora,
  mac_address,
  GREATEST(kwh_fin - kwh_inicio, 0) AS kwh_consumidos
FROM hourly
ORDER BY hora;
```

### 9.4. Picos de potencia

```sql
SELECT
  d.mac_address,
  d.name,
  MAX(r.power_w) AS pico_w
FROM devices d
JOIN readings r ON r.device_id = d.id
WHERE r.time >= NOW() - INTERVAL '7 days'
GROUP BY d.mac_address, d.name
ORDER BY pico_w DESC;
```

### 9.5. Consumo nocturno aproximado

```sql
SELECT
  d.mac_address,
  DATE_TRUNC('day', r.time AT TIME ZONE 'Europe/Madrid') AS dia_local,
  MIN(r.energy_total_kwh) AS kwh_inicio,
  MAX(r.energy_total_kwh) AS kwh_fin,
  GREATEST(MAX(r.energy_total_kwh) - MIN(r.energy_total_kwh), 0) AS kwh_fantasma
FROM readings r
JOIN devices d ON d.id = r.device_id
WHERE (r.time AT TIME ZONE 'Europe/Madrid')::time >= TIME '00:00'
  AND (r.time AT TIME ZONE 'Europe/Madrid')::time < TIME '06:00'
GROUP BY d.mac_address, dia_local
ORDER BY dia_local DESC, d.mac_address;
```

En producción conviene ajustar la zona horaria por tarifa, como ya hace `CalendarResolverService` en Java.

## 10. Mejoras futuras de base de datos

- Añadir índice sobre `(device_id, time DESC)` si crecen las búsquedas de última lectura.
- Crear continuous aggregates para medias por minuto u hora.
- Aplicar políticas de retención si se acumulan lecturas antiguas sin uso.
- Completar seeds regulatorios de `CANARIAS`, `CEUTA`, `MELILLA`, `6.1TD` y `6.2TD`.
- Mover informes complejos a consultas nativas si la explotación analítica aumenta.

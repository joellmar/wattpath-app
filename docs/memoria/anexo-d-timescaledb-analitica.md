# Anexo D. TimescaleDB y consultas analíticas

Este anexo documenta la parte de persistencia de Wattimizer, con especial atención a la tabla temporal `readings`, su conversión a hypertable de TimescaleDB y las consultas usadas para calcular coste energético, consumo fantasma y alertas.

## D.1. Motor y estrategia de persistencia

El proyecto usa PostgreSQL con TimescaleDB:

```yaml
image: timescale/timescaledb-ha:pg17
```

Hibernate está configurado con:

```properties
spring.jpa.hibernate.ddl-auto=update
```

La decisión técnica es híbrida:

- Hibernate crea y actualiza las tablas JPA.
- Los scripts SQL añaden lo que Hibernate no puede expresar bien: extensión TimescaleDB, hypertable, constraints, índices y seed del calendario tarifario.

## D.2. Modelo relacional principal

```mermaid
erDiagram
    users ||--o| tariffs : "tariff_id"
    users ||--o{ devices : "user_id"
    users ||--o{ alerts : "user_id"
    users ||--o{ federated_identities : "user_id"
    devices ||--o{ readings : "device_id"
    devices ||--o{ alerts : "device_id"
    tariffs ||--o{ periods : "tariff_id"
    tariffs ||--o{ tariff_contracted_powers : "tariff_id"
```

## D.3. Tabla `readings`

Entidad: `Reading`.

| Columna | Tipo lógico | Descripción |
|---|---|---|
| `time` | `Instant` | Instante de la lectura. Forma parte de la clave primaria. |
| `device_id` | FK a `devices` | Dispositivo medido. Forma parte de la clave primaria. |
| `power_w` | `NUMERIC(10,2)` | Potencia instantánea en vatios. |
| `energy_total_kwh` | `NUMERIC(14,4)` | Odómetro acumulado de energía. |
| `is_on` | `boolean` | Estado del enchufe cuando la fuente lo informa. |

La clave primaria compuesta se declara con `@IdClass(ReadingId.class)`. Esto encaja con una serie temporal porque una lectura se identifica por el dispositivo y el instante.

## D.4. Conversión a hypertable

Script: `backend/src/main/resources/db/dev-seed/01-hypertable.sql`.

```sql
SELECT create_hypertable('readings', 'time');
```

El propio script indica el orden correcto:

1. Ejecutar extensión TimescaleDB.
2. Dejar que Hibernate cree la tabla `readings`.
3. Convertir `readings` en hypertable antes de que lleguen datos MQTT.

La tabla debe estar vacía. Si ya tuviera datos, el script documenta la alternativa:

```sql
SELECT create_hypertable('readings', 'time', migrate_data => true);
```

### Supuestos técnicos

| Aspecto | Estado actual |
|---|---|
| Dimensión de partición | `time` |
| Partición adicional por dispositivo | No configurada |
| Compresión | No configurada |
| Retención | No configurada |
| Agregados continuos | No configurados |
| Uso de `time_bucket` | No aparece en el backend actual |

TimescaleDB se usa como base para almacenar series temporales, pero las consultas analíticas se calculan todavía en Java.

## D.5. Tabla `devices`

Entidad: `Device`.

| Columna | Descripción |
|---|---|
| `id` | Identificador interno. |
| `user_id` | Usuario propietario. Puede ser nulo en dispositivos todavía no reclamados; la ingesta MQTT física actual funciona correctamente cuando el Shelly ya existe como dispositivo registrado o sembrado. |
| `name` | Nombre visible. |
| `mac_address` | MAC única del dispositivo. |
| `is_on` | Estado actual. |
| `is_simulated` | Indica si lo procesa el job de simulación. |
| `simulation_profile` | Perfil de simulación usado para calcular potencia. |

El borrado de dispositivos se ha implementado de forma explícita en `DeviceService.deleteById`:

1. Borra lecturas por MAC.
2. Borra alertas por `device_id`.
3. Borra el dispositivo.

Esta secuencia evita errores de clave foránea y deja la base limpia.

## D.6. Usuarios, tarifas y periodos

### `users`

Contiene:

- `username` único.
- `password` cifrado.
- `role`: `ROLE_USER` o `ROLE_ADMIN`.
- `active`.
- `tariff_id` opcional hacia una tarifa privada.

### `tariffs`

Tabla principal de contrato energético:

| Campo | Uso |
|---|---|
| `name` | Nombre de tarifa. |
| `market` | Mercado libre o regulado. |
| `access_tariff_code` | Peaje: `2.0TD`, `3.0TD`, `6.1TD`, `6.2TD`. |
| `geographic_zone` | Zona española: Península, Baleares, Canarias, Ceuta o Melilla. |
| `energyCompany` | Comercializadora. |

### `periods`

Guarda precio por kWh:

```text
tariff_id + period_code -> price_kwh
```

### `tariff_contracted_powers`

Guarda potencia contratada por periodo:

```text
tariff_id + period_code -> contracted_power_kw
```

Se usa en alertas de maxímetro.

## D.7. Calendario regulatorio `tariff_calendar_slots`

Esta tabla no pertenece a una tarifa concreta. Es una dimensión global que resuelve:

```text
access_tariff_code + geographic_zone + month_number + day_type + hora local
    -> period_code
```

Campos:

| Campo | Significado |
|---|---|
| `access_tariff_code` | Peaje de acceso. |
| `geographic_zone` | Zona geográfica. |
| `month_number` | Mes. |
| `season_code` | Temporada regulatoria. |
| `day_type` | Tipo de día: `A`, `B`, `B1`, `C`, `D`. |
| `period_code` | Periodo resultante. |
| `start_time`, `end_time` | Intervalo horario. |

El seed `seed-tariff-calendar-slots.sql` cubre `2.0TD` y `3.0TD` para `PENINSULA` e `ISLAS_BALEARES`. El esquema permite más zonas y peajes, pero si faltan filas de calendario el cálculo entra en modo degradado y no suma coste para esos tramos.

## D.8. Repositorio de lecturas

`ReadingRepository` define:

```java
Optional<Reading> findFirstByDeviceMacAddressOrderByTimeDesc(String macAddress);
```

Sirve para obtener la última lectura de un dispositivo.

```java
Optional<Reading> findByTimeAndDeviceMacAddress(Instant time, String macAddress);
```

Permite localizar una lectura concreta por clave lógica.

```java
@Query("SELECT r FROM Reading r WHERE r.device.macAddress = :macAddress " +
       "AND r.time >= :start AND r.time <= :end ORDER BY r.time ASC")
List<Reading> findReadingsInInterval(...)
```

Es la consulta principal para histórico, coste y consumo fantasma.

```java
@Query("DELETE FROM Reading r WHERE r.time = :time AND r.device.macAddress = :macAddress")
Long deleteByTimeAndDeviceMacAddress(...)
```

Borra una lectura concreta.

```java
@Query("DELETE FROM Reading r WHERE r.device.macAddress = :macAddress")
int deleteAllByDeviceMacAddress(...)
```

Se usa en el borrado en cascada de dispositivos.

## D.9. Consultas REST apoyadas en `readings`

| Endpoint | Método de servicio | Consulta base |
|---|---|---|
| `GET /api/v1/readings/latest/{mac}` | `ReadingService.findByDevice` | Última lectura por MAC. |
| `GET /api/v1/readings/device/{mac}/recent` | `ReadingService.listRecentByMacAddress` | Intervalo `now - seconds` a `now`. |
| `GET /api/v1/readings/search` | `ReadingService.findByTimeAndMacAddress` | `time + macAddress`. |
| `DELETE /api/v1/readings/search` | `ReadingService.deleteByTimeAndMacAddress` | Borrado por `time + macAddress`. |
| `GET /api/v1/analytics/cost` | `ConsumptionService.calculateCostInPeriod` | Lecturas ordenadas del intervalo. |
| `GET /api/v1/analytics/ghost-consumption` | `ConsumptionService.calculateGhostCost` | Lecturas ordenadas del intervalo. |

## D.10. Cálculo de coste energético

El coste no se calcula a partir de `powerW` instantáneo, sino del incremento del odómetro `energyTotalKwh`. Esto es más estable, porque el Shelly ya acumula la energía.

Algoritmo:

```text
readings = lecturas ordenadas entre start y end
si readings.size < 2 -> 0

para cada par consecutive previous/current:
    delta = current.energyTotalKwh - previous.energyTotalKwh
    si delta <= 0 -> ignorar
    period = CalendarResolverService.resolveApplicablePeriod(tariff, current.time)
    coste += delta * period.priceKwh

redondear coste a 2 decimales
```

El servicio devuelve `0` si:

- Hay menos de dos lecturas.
- El dispositivo no tiene usuario.
- El usuario no tiene tarifa.
- No se puede resolver el periodo del calendario.

Esta decisión evita romper el dashboard por una configuración incompleta, aunque también puede ocultar errores de configuración si no se revisan los logs.

## D.11. Consumo fantasma

`calculateGhostCost` usa el mismo cálculo por deltas, pero añade una condición:

```text
hora local >= 0 && hora local < 6
```

La hora local se resuelve según la zona del contrato:

- Península/Baleares: `Europe/Madrid`.
- Canarias: `Atlantic/Canary`.

No se usa directamente el periodo valle regulatorio, porque el consumo fantasma se define funcionalmente como actividad nocturna entre 00:00 y 05:59.

## D.12. Resolución de periodo tarifario

`CalendarResolverService` convierte un `Instant` a hora local y consulta `TariffCalendarSlotRepository`.

La consulta JPQL busca una fila que coincida con:

- Código de peaje.
- Zona geográfica.
- Mes.
- Tipo de día.
- Intervalo horario.

También contempla un caso especial para días tipo `D`, donde puede representarse el día completo.

El resultado es un `period_code`, que después se cruza con `PeriodRepository.findByTariffIdAndPeriodCode`.

## D.13. Alertas de maxímetro

Cada vez que se guarda una lectura, `AlertService.checkPowerThreshold`:

1. Verifica que la lectura tenga dispositivo, usuario, tarifa, potencia y tiempo.
2. Resuelve el periodo aplicable.
3. Busca `contracted_power_kw` para ese periodo.
4. Convierte `powerW` a kW.
5. Si `currentPowerKw > contractedPowerKw`, crea una alerta `OVERPOWER`.

Mensaje generado:

```text
¡ALERTA CRÍTICA DE MAXÍMETRO! El dispositivo ... ha registrado un pico ...
```

Después de guardarla, la emite por `/topic/alerts/{username}`.

## D.14. Scripts SQL de tarifas

### `tariffs-td-schema.sql`

Este script endurece el modelo creado por Hibernate:

- Elimina columnas legacy.
- Añade `access_tariff_code` y `geographic_zone`.
- Añade constraints para peajes, zonas, periodos y potencias.
- Crea índices para búsquedas de calendario y relaciones tarifarias.

### `seed-tariff-calendar-slots.sql`

Carga el calendario regulatorio. Usa `ON CONFLICT DO NOTHING`, así que puede ejecutarse varias veces sin duplicar filas.

Cobertura actual:

| Peajes | Zonas |
|---|---|
| `2.0TD`, `3.0TD` | `PENINSULA`, `ISLAS_BALEARES` |

El esquema permite `CANARIAS`, `CEUTA`, `MELILLA`, `6.1TD` y `6.2TD`, pero no todos tienen seed completo.

## D.15. Observaciones de rendimiento

- `findReadingsInInterval` está ordenado por tiempo y se beneficia de la hypertable, pero el cálculo sigue siendo O(n) en Java.
- `ReadingService.listByUsername` usa `findAll()` y filtra en memoria; para muchos datos debería pasar a una consulta por usuario.
- No hay agregados continuos para dashboard diario/mensual.
- No hay `time_bucket` para resumir puntos en rangos largos.
- No hay política de retención; la tabla crecerá indefinidamente si se mantiene el sistema activo.

## D.16. Mejoras futuras sobre TimescaleDB

Las mejoras más coherentes con el código actual serían:

1. Crear índices específicos sobre `readings(device_id, time DESC)`.
2. Sustituir filtrados en memoria por consultas JPQL o SQL nativo.
3. Añadir `time_bucket` para agregados de potencia media y consumo por tramo.
4. Crear agregados continuos diarios para coste estimado.
5. Configurar compresión y retención para lecturas antiguas.
6. Completar seeds de calendario para Canarias, Ceuta, Melilla y peajes 6.xTD.

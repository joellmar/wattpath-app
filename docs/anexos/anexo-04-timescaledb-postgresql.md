# Anexo 04. Persistencia analítica con PostgreSQL y TimescaleDB

## 1. Papel de la base de datos en el proyecto

Wattimizer usa PostgreSQL con TimescaleDB como base de datos principal. En `docker-compose.yml` el servicio se define con la imagen:

```yaml
timescaledb:
  image: timescale/timescaledb-ha:pg17
  container_name: db_iot
  environment:
    POSTGRES_DB: wattimizer_db
    POSTGRES_USER: postgres
    POSTGRES_PASSWORD: ${DB_PASSWORD}
```

El backend se conecta a este contenedor mediante:

```yaml
SPRING_DATASOURCE_URL=jdbc:postgresql://timescaledb:5432/wattimizer_db
```

En el repositorio no hay migraciones Flyway, Liquibase ni scripts `.sql` con `create_hypertable`. Por tanto, la estructura documentada aquí se basa en las entidades JPA, repositorios y servicios existentes. La tabla temporal del dominio es `readings`, que por su clave temporal y el uso de TimescaleDB es la candidata natural a hypertable, aunque la conversión explícita no está versionada en el código.

## 2. Configuración JPA

Archivo: `backend/src/main/resources/application.properties`

```properties
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
```

`ddl-auto=validate` indica que Hibernate no crea ni modifica tablas. Solo valida que el esquema real de la base de datos coincida con las entidades. Esta decisión es coherente con TimescaleDB, porque una hypertable no se crea correctamente con Hibernate: necesita SQL específico de TimescaleDB fuera del mapeo JPA.

El propio comentario del archivo de configuración lo deja claro:

```properties
# "update" intenta actualizar la BD ... pero NO crea hipertablas automáticamente
# "validate" solo comprueba que coincidan
```

## 3. Tabla temporal principal: `readings`

Entidad: `backend/src/main/java/com/joselumartos/jwtauthbackenddemo/entities/Reading.java`

```java
@Entity
@Table(name = "readings")
@IdClass(ReadingId.class)
public class Reading {
    @Id
    @Column(nullable = false, updatable = false)
    private Instant time;

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
```

Clave compuesta:

```java
public class ReadingId implements Serializable {
    private Long device;
    private Instant time;
}
```

Lectura conceptual de la tabla:

| Columna | Tipo Java | Papel |
| --- | --- | --- |
| `time` | `Instant` | Marca temporal de la lectura, pensada para particionado temporal. |
| `device_id` | `Long` / FK | Dispositivo que generó la lectura. Forma parte de la clave. |
| `power_w` | `BigDecimal(10,2)` | Potencia instantánea en vatios. |
| `energy_total_kwh` | `BigDecimal(14,4)` | Energía acumulada normalizada a kWh. |
| `is_on` | `Boolean` | Estado lógico del enchufe. |

La clave `time + device_id` evita duplicar dos lecturas del mismo dispositivo en el mismo instante. Para TimescaleDB, `time` es la columna que debería usarse como dimensión temporal de la hypertable.

SQL orientativo coherente con el modelo JPA:

```sql
-- Este SQL no aparece versionado en el repositorio; representa la estructura
-- que las entidades esperan encontrar cuando Hibernate valida el esquema.
CREATE TABLE readings (
  time timestamptz NOT NULL,
  device_id bigint NOT NULL,
  power_w numeric(10, 2),
  energy_total_kwh numeric(14, 4),
  is_on boolean,
  PRIMARY KEY (time, device_id),
  FOREIGN KEY (device_id) REFERENCES devices(id)
);

-- La conversión a hypertable debe existir en la base real si se quiere
-- aprovechar TimescaleDB, pero no está en ningún script del repositorio.
SELECT create_hypertable('readings', 'time', if_not_exists => TRUE);
```

## 4. Tablas relacionales de apoyo

### 4.1 `devices`

Entidad: `Device.java`

| Columna | Origen en entidad | Descripción |
| --- | --- | --- |
| `id` | `BaseEntity` | Identificador técnico. |
| `user_id` | `@ManyToOne UserEntity user` | Usuario propietario del dispositivo. Puede ser nulo durante la ingesta inicial. |
| `name` | `String name` | Nombre visible del enchufe. |
| `mac_address` | `String macAddress` | Dirección única del dispositivo Shelly. |
| `is_on` | `Boolean isOn` | Estado virtual del dispositivo. |
| `created_at`, `updated_at`, `created_by`, `updated_by` | `BaseEntity` | Campos de auditoría. |

La MAC tiene restricción única, porque identifica físicamente el enchufe.

### 4.2 `users`

Entidad: `UserEntity.java`

| Columna | Descripción |
| --- | --- |
| `id` | Identificador heredado de `BaseEntity`. |
| `tariff_id` | Tarifa asignada al usuario. |
| `username` | Nombre de usuario, único y obligatorio. |
| `password` | Contraseña cifrada. |
| `role` | Rol `ROLE_USER` o `ROLE_ADMIN`. |
| `active` | Usuario habilitado o deshabilitado. |
| `created_at`, `updated_at`, `created_by`, `updated_by` | Campos heredados de `BaseEntity`. |

`UserEntity` implementa `UserDetails`, por lo que participa directamente en Spring Security.

### 4.3 `tariffs`

Entidad: `Tariff.java`

| Columna | Descripción |
| --- | --- |
| `id` | Identificador heredado de `BaseEntity`. |
| `name` | Nombre de la tarifa. |
| `type` | Tipo comercial, por ejemplo `3.0TD`. |
| `market` | Mercado libre o regulado. |
| `contracted_power_kw` | Potencia contratada en kW. |
| `energy_company` | Comercializadora. En la entidad aparece como propiedad Java `energyCompany`. |
| `created_at`, `updated_at`, `created_by`, `updated_by` | Campos heredados de `BaseEntity`. |

La potencia contratada se usa para generar alertas cuando una lectura supera el límite.

### 4.4 `periods`

Entidad: `Period.java`

| Columna | Descripción |
| --- | --- |
| `id` | Identificador del periodo. |
| `tariff_id` | Tarifa a la que pertenece. |
| `name` | Nombre del tramo, por ejemplo `P1 Punta`. |
| `price_kwh` | Precio por kWh con escala de 6 decimales. |
| `start_hour` | Inicio del tramo horario. |
| `end_hour` | Fin del tramo horario. |
| `day_type` | Tipo de día: `WEEKDAY`, `WEEKEND` o `HOLIDAY`. |
| `start_month` | Mes inicial de aplicación. |
| `end_month` | Mes final de aplicación. |

El código actual consulta por hora, pero no filtra todavía por `day_type` ni por mes en `findApplicablePeriod`.

### 4.5 `alerts`

Entidad: `Alert.java`

| Columna | Descripción |
| --- | --- |
| `id` | Identificador heredado de `BaseEntity`. |
| `user_id` | Usuario al que pertenece la alerta. |
| `device_id` | Dispositivo que provocó la alerta. |
| `type` | Tipo de alerta, actualmente `OVERPOWER`. |
| `message` | Mensaje descriptivo. |
| `created_at` | Fecha usada por el DTO `AlertDto`. |
| `updated_at`, `created_by`, `updated_by` | Campos heredados de `BaseEntity`. |

## 5. Consultas sobre lecturas temporales

Repositorio: `backend/src/main/java/com/joselumartos/jwtauthbackenddemo/repositories/ReadingRepository.java`

### 5.1 Última lectura de un dispositivo

```java
Optional<Reading> findFirstByDeviceMacAddressOrderByTimeDesc(String macAddress);
```

Consulta derivada por Spring Data. Ordena por `time` descendente y devuelve la lectura más reciente de la MAC indicada. En una hypertable, esta consulta se beneficia de tener índice por dispositivo y tiempo.

Equivalente SQL:

```sql
SELECT r.*
FROM readings r
JOIN devices d ON d.id = r.device_id
WHERE d.mac_address = :macAddress
ORDER BY r.time DESC
LIMIT 1;
```

### 5.2 Búsqueda por clave compuesta

```java
Optional<Reading> findByTimeAndDeviceMacAddress(Instant time, String macAddress);
```

Se usa cuando el controlador necesita localizar una lectura exacta por instante y MAC. Encaja con la clave compuesta `time + device_id`.

Equivalente SQL:

```sql
SELECT r.*
FROM readings r
JOIN devices d ON d.id = r.device_id
WHERE r.time = :time
  AND d.mac_address = :macAddress;
```

### 5.3 Intervalo temporal para analítica

```java
@Query("SELECT r FROM Reading r WHERE r.device.macAddress = :macAddress " +
        "AND r.time >= :start AND r.time <= :end ORDER BY r.time ASC")
List<Reading> findReadingsInInterval(
        @Param("macAddress") String macAddress,
        @Param("start") Instant start,
        @Param("end") Instant end);
```

Esta es la consulta base de los cálculos económicos. Devuelve todas las lecturas de un dispositivo entre dos instantes, ordenadas de más antigua a más reciente. Ese orden es obligatorio porque el servicio calcula diferencias entre lecturas consecutivas.

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

### 5.4 Borrado por clave compuesta

```java
@Modifying
@Transactional
@Query("DELETE FROM Reading r WHERE r.time = :time AND r.device.macAddress = :macAddress")
Long deleteByTimeAndDeviceMacAddress(@Param("time") Instant time, @Param("macAddress") String macAddress);
```

Se utiliza desde `ReadingController.DELETE /api/v1/readings/search`. Antes de borrar, `ReadingService` comprueba que la lectura existe.

## 6. Consulta de periodo tarifario

Repositorio: `PeriodRepository.java`

```java
@Query("SELECT p FROM Period p WHERE p.tariff.id = :tariffId AND " +
        "((p.startHour <= :currentTime AND p.endHour >= :currentTime) OR " +
        "(p.startHour > p.endHour AND (:currentTime >= p.startHour OR :currentTime <= p.endHour)))")
Optional<Period> findApplicablePeriod(@Param("tariffId") Long tariffId,
                                      @Param("currentTime") LocalTime currentTime);
```

La consulta cubre dos casos:

1. Tramos normales, donde `startHour <= endHour`.
2. Tramos que cruzan medianoche, donde `startHour > endHour`.

Esto permite representar periodos como una franja nocturna que empieza un día y termina al día siguiente.

Equivalente SQL aproximado:

```sql
SELECT p.*
FROM periods p
WHERE p.tariff_id = :tariffId
  AND (
    (p.start_hour <= :currentTime AND p.end_hour >= :currentTime)
    OR
    (p.start_hour > p.end_hour AND (:currentTime >= p.start_hour OR :currentTime <= p.end_hour))
  );
```

## 7. Cálculo de coste total

Servicio: `backend/src/main/java/com/joselumartos/jwtauthbackenddemo/services/ConsumptionService.java`

Método:

```java
public BigDecimal calculateCostInPeriod(String macAddress, Instant start, Instant end)
```

Pasos reales:

1. Obtiene lecturas con `findReadingsInInterval`.
2. Si hay menos de dos lecturas, devuelve `BigDecimal.ZERO`.
3. Busca el dispositivo por MAC.
4. Comprueba que el dispositivo tenga usuario y tarifa.
5. Recorre las lecturas desde la segunda posición.
6. Calcula `deltaKwh` entre lectura actual y anterior.
7. Ignora deltas nulos, negativos o cero.
8. Convierte `Instant` a zona `Europe/Madrid`.
9. Busca el periodo tarifario aplicable.
10. Suma `deltaKwh * priceKwh`.
11. Devuelve el total con escala 2.

Fragmento central:

```java
BigDecimal deltaKwh = current.getEnergyTotalKwh().subtract(previous.getEnergyTotalKwh());

if (deltaKwh.compareTo(BigDecimal.ZERO) <= 0) continue;

ZonedDateTime zonedDateTime = current.getTime().atZone(ZoneId.of("Europe/Madrid"));
LocalTime localTime = zonedDateTime.toLocalTime();

Optional<Period> periodOpt = periodRepository.findApplicablePeriod(tariff.getId(), localTime);
if (periodOpt.isEmpty()) continue;

BigDecimal stepCost = deltaKwh.multiply(periodOpt.get().getPriceKwh());
totalCost = totalCost.add(stepCost);
```

La decisión de trabajar con energía acumulada evita depender de la frecuencia exacta de muestreo. Si el Shelly emite cada pocos segundos o cada minuto, el delta entre contadores sigue representando energía consumida en ese intervalo.

## 8. Cálculo de consumo fantasma

Método:

```java
public BigDecimal calculateGhostCost(String macAddress, Instant start, Instant end)
```

El cálculo es parecido al coste total, pero solo contabiliza lecturas entre las `00:00` y las `05:59` en zona `Europe/Madrid`:

```java
int hour = zonedDateTime.getHour();

if (hour >= 0 && hour < 6) {
    BigDecimal deltaKwh = current.getEnergyTotalKwh().subtract(previous.getEnergyTotalKwh());
    ...
    ghostCost = ghostCost.add(stepCost);
}
```

El objetivo funcional es detectar gasto durante la franja nocturna, cuando normalmente debería haber menos actividad. En la memoria del proyecto puede explicarse como indicador de consumo fantasma o consumo fuera de horario operativo.

## 9. Relación entre hypertable y consultas actuales

La implementación actual no usa funciones específicas de TimescaleDB como `time_bucket`, `first`, `last` o continuous aggregates. Las consultas son JPQL estándar y se ejecutan sobre PostgreSQL. Aun así, encajan con una hypertable porque filtran por rango temporal y MAC, que es justo el patrón normal de consulta en series temporales.

Puntos importantes:

- `readings.time` es la dimensión temporal natural.
- `readings.device_id` permite separar series por dispositivo.
- `findReadingsInInterval` es la consulta analítica principal.
- Los cálculos se hacen en Java, no en SQL agregada.
- No hay migración versionada que garantice la creación de la hypertable.

Si se quisiera dejar el esquema completamente reproducible, faltaría añadir una migración SQL con:

```sql
CREATE EXTENSION IF NOT EXISTS timescaledb;
SELECT create_hypertable('readings', 'time', if_not_exists => TRUE);
```

Este SQL no forma parte del código actual, por lo que se documenta como hueco técnico y no como funcionalidad ya versionada.

## 10. Flujo de datos analítico

```text
1. MQTT guarda lecturas en readings.
2. Cada lectura conserva time, device_id, power_w y energy_total_kwh.
3. Angular solicita /api/v1/analytics/cost o /ghost-consumption.
4. ConsumptionController valida que la MAC pertenezca al usuario.
5. ConsumptionService consulta readings por MAC y rango temporal.
6. El servicio calcula deltas de energía acumulada.
7. PeriodRepository decide el precio del tramo horario.
8. El backend devuelve el importe en euros al dashboard.
```

## 11. Observaciones técnicas para la memoria

- El modelo está preparado para series temporales porque `readings` usa una marca temporal como parte de su clave.
- TimescaleDB está presente en la infraestructura Docker, pero la creación de hypertables no está automatizada en el repositorio.
- La analítica económica se calcula en la capa de servicio para poder aplicar reglas de negocio de tarifas y propiedad de dispositivos.
- El diseño actual prioriza claridad y trazabilidad frente a consultas SQL avanzadas. Para el volumen de un MVP académico es razonable, aunque TimescaleDB permitiría optimizar agregaciones futuras.

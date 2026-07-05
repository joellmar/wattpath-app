# Anexo C. Ingesta de telemetria MQTT y simulacion IoT

Este anexo documenta el flujo de telemetria de Wattimizer. El codigo principal esta en `MqttConfig`, `DeviceMessageHandler`, `ReadingService`, `SimulatedTelemetryProcessor`, `IotTelemetrySimulationJob` y `TelemetryBroadcaster`.

## 1. Objetivo del modulo

El modulo IoT convierte mensajes tecnicos del Shelly Plug S Gen3 o lecturas simuladas en datos utiles para la aplicacion:

1. Lectura persistida en TimescaleDB.
2. Actualizacion en tiempo real por WebSocket.
3. Evaluacion de alerta por exceso de potencia.
4. Datos disponibles para analitica REST.

La decision importante es que MQTT y simulacion terminan pasando por la misma salida funcional: una entidad `Reading` y un DTO `ReadingResponse`.

## 2. Configuracion MQTT

Clase: `backend/src/main/java/com/joselumartos/jwtauthbackenddemo/config/MqttConfig.java`

Propiedades:

```properties
mqtt.url=${MQTT_URL:tcp://localhost:1883}
mqtt.username=${MQTT_USER:gateway-service}
mqtt.password=${MQTT_PASSWORD:s3cr3t}
```

En Docker:

```yaml
MQTT_URL: tcp://mosquitto:1883
MQTT_USER: ${PROD_MQTT_USER}
MQTT_PASSWORD: ${PROD_MQTT_PASSWORD}
```

Configuracion del cliente Paho:

- `automaticReconnect=true`: intenta reconectar si Mosquitto se cae.
- `cleanSession=true`: no conserva sesion MQTT anterior.
- `clientId`: `backend-spring-iot`.
- Topic suscrito: `shellyplugsg3-9070694d3590/#`.
- QoS: `1`.

El topic esta fijado al Shelly usado en el proyecto. Para produccion multi-dispositivo real habria que generalizarlo a una configuracion externa o a un patron wildcard mas amplio.

## 3. Spring Integration Flow

`mqttInboundFlow` recibe mensajes MQTT y enruta segun el sufijo del topic:

| Sufijo MQTT | Tipo DTO | Canal interno | Handler |
| --- | --- | --- | --- |
| `/events/rpc` | `EventsRpc` | `eventsRpcChannel` | `handleEventsRpc` |
| `/status/switch:0` | `Status` | `statusChannel` | `handleStatus` |
| Otros | Sin transformar | `nullChannel` | Se descartan |

La transformacion JSON se hace dentro del flow:

```java
.subFlowMapping("EVENTS", eventsBranch -> eventsBranch
    .transform(Transformers.fromJson(EventsRpc.class))
    .channel("eventsRpcChannel")
)
.subFlowMapping("STATUS", statusBranch -> statusBranch
    .transform(Transformers.fromJson(Status.class))
    .channel("statusChannel")
)
```

Esta separacion evita llenar los handlers de condicionales sobre el topic. Cada canal recibe un DTO ya tipado.

## 4. Naturaleza asincrona del flujo

La ingesta es asincrona respecto al broker MQTT porque el backend no hace polling ni espera desde una peticion REST. El adaptador de Paho recibe mensajes cuando llegan y Spring Integration los introduce en el flow.

Dentro de la aplicacion, sin embargo, los canales son `DirectChannel`. Esto significa:

- No hay cola persistente intermedia.
- No se usa `@Async`.
- No hay `ExecutorChannel`.
- El mensaje se procesa de forma inmediata en la invocacion del canal.

Esta decision es razonable para el MVP: simplifica transacciones y mantiene el orden por mensaje. Si el volumen de dispositivos creciera, se podria evolucionar a canales con executor, colas o una arquitectura con Kafka/RabbitMQ.

## 5. Procesamiento de `events/rpc`

Handler:

```java
@Transactional
@ServiceActivator(inputChannel = "eventsRpcChannel")
public void handleEventsRpc(Message<EventsRpc> mqttMessage) {
    EventsRpc payload = mqttMessage.getPayload();
    Reading reading = readingService.saveEntity(payload);
    broadcaster.broadcast(readingResponseMapper.toDto(reading));
    alertService.checkPowerThreshold(reading);
}
```

Flujo:

1. `EventsRpc` llega ya parseado.
2. `ReadingService.saveEntity(payload)` mapea el payload a entidad.
3. Si el dispositivo no existe, el servicio puede auto-provisionarlo.
4. Se persiste la lectura.
5. `TelemetryBroadcaster` publica en STOMP.
6. `AlertService` evalua si la potencia supera el umbral contratado.

Mapeo conceptual:

| Campo Shelly | Campo interno |
| --- | --- |
| `src` | MAC del dispositivo, extrayendo la parte posterior a `shellyplugsg3-`. |
| `params.ts` | `Reading.time`. |
| `params.switch:0.apower` | `Reading.powerW`. |
| `params.switch:0.aenergy.total` | `Reading.energyTotalKwh` tras convertir Wh a kWh. |

## 6. Procesamiento de `status/switch:0`

Handler:

```java
@Transactional
@ServiceActivator(inputChannel = "statusChannel")
public void handleStatus(Message<Status> mqttMessage) {
    String topic = mqttMessage.getHeaders().get(MqttHeaders.RECEIVED_TOPIC, String.class);
    String macAddress = (topic != null) ? topic.split("/")[0].split("-")[1] : null;
    DeviceDto deviceDto = deviceService.findByMacAddress(macAddress);
    Reading reading = readingService.saveEntity(deviceDto, payload);
    broadcaster.broadcast(readingResponseMapper.toDto(reading));
    alertService.checkPowerThreshold(reading);
}
```

El topic se usa para recuperar la MAC. En este tipo de mensaje el instante de la lectura no viene como timestamp principal, asi que el sistema usa `Instant.now()` al guardar.

Mapeo conceptual:

| Campo `Status` | Campo interno |
| --- | --- |
| `output` | `Reading.isOn`. |
| `apower` | `Reading.powerW`. |
| `aenergy.total` | `Reading.energyTotalKwh` en kWh. |
| topic | MAC del dispositivo. |

## 7. Persistencia comun

Entidad:

```java
@Entity
@Table(name = "readings")
@IdClass(ReadingId.class)
public class Reading {
    @Id
    private Instant time;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    private Device device;

    private BigDecimal powerW;
    private BigDecimal energyTotalKwh;
    private Boolean isOn;
}
```

La clave compuesta `(time, device_id)` permite guardar lecturas de distintos dispositivos en el mismo instante. TimescaleDB usa `time` para particionar la hypertable.

## 8. Emision WebSocket

`TelemetryBroadcaster` envia el `ReadingResponse` a:

```text
/topic/readings/{macAddress}
```

El frontend se suscribe con `WebsocketService.watchReadings(macAddress)`. Asi la lectura llega al dashboard sin que Angular tenga que consultar continuamente por HTTP.

Las alertas se publican a:

```text
/topic/alerts/{username}
```

Actualmente la vista de alertas se alimenta por REST, pero el backend ya contempla broadcast para una evolucion en tiempo real.

## 9. Alertas de maximetro

Despues de cada lectura, `AlertService.checkPowerThreshold(reading)`:

1. Obtiene el usuario y la tarifa del dispositivo.
2. Resuelve el periodo aplicable con `CalendarResolverService`.
3. Busca la potencia contratada en `tariff_contracted_powers`.
4. Convierte `powerW` a kW.
5. Si la potencia medida supera la contratada, crea alerta `OVERPOWER`.

La alerta no se calcula solo con un numero fijo. Depende del periodo P1-P6, porque la potencia contratada puede variar segun el contrato.

## 10. Simulacion IoT

La simulacion permite demostrar el proyecto sin hardware fisico.

### `IotTelemetrySimulationJob`

Job programado:

```java
@Scheduled(fixedRateString = "${simulation.interval-ms:5000}")
```

Propiedades:

```properties
simulation.enabled=${SIMULATION_ENABLED:true}
simulation.interval-ms=${SIMULATION_INTERVAL_MS:5000}
```

El job:

1. Comprueba si la simulacion esta habilitada.
2. Carga dispositivos con `is_simulated=true`.
3. Procesa cada dispositivo con aislamiento de errores.

### `SimulatedTelemetryProcessor`

Este servicio genera una lectura por dispositivo:

1. Lee `simulationProfile` y `isOn`.
2. Calcula `powerW` con `SimulationProfileRegistry`.
3. Busca la ultima lectura para conservar el odometro acumulado.
4. Calcula el nuevo `energyTotalKwh`.
5. Guarda con `ReadingService.saveSimulatedReading`.
6. Publica por WebSocket y evalua alertas.

Formula usada:

```text
delta_kWh = powerW * (intervalMs / 1000) / 1000 / 3600
```

El procesador usa `@Transactional(propagation = Propagation.REQUIRES_NEW)`. El motivo es claro: si falla un simulador, no debe revertirse todo el tick ni bloquear el resto de dispositivos.

### Desfase temporal por dispositivo

```java
Instant readingTime = tickStart.plusMillis(device.getId() % 1000L);
```

Este pequeno desfase evita colisiones de clave primaria cuando varios simuladores generan lectura en el mismo tick.

## 11. Perfiles simulados

| Perfil | Intencion |
| --- | --- |
| `SINE_WAVE` | Senal variable suave para demo general. |
| `OVEN` | Ciclo de alta potencia y mantenimiento. |
| `WASHING_MACHINE` | Consumo por fases. |
| `TELEVISION` | Consumo moderado estable. |
| `FAN` | Carga pequena-media. |
| `DESKTOP_PC` | Consumo de equipo informatico. |
| `FRIDGE` | Ciclos intermitentes de compresor. |
| `STANDBY` | Consumo fantasma bajo. |
| `CONSTANT_HIGH_LOAD` | 3500 W constantes para probar alertas. |

El pack demo crea un dispositivo por perfil si el usuario todavia no lo tiene. Esto ayuda a presentar la aplicacion con datos variados.

## 12. Flujo completo de una lectura

```text
Shelly o simulador
  -> ReadingService
  -> readings hypertable
  -> TelemetryBroadcaster
  -> /topic/readings/{mac}
  -> TelemetryStore
  -> Dashboard Chart.js
```

En paralelo:

```text
Reading
  -> AlertService
  -> alerts
  -> /topic/alerts/{username}
  -> GET /api/v1/alerts para la vista actual
```

## 13. Limitaciones actuales

- Solo se escucha un topic fisico hardcodeado del Shelly de desarrollo.
- MQTT se expone en el puerto 1883 sin TLS en `docker-compose.yml`; esta marcado como deuda tecnica.
- No hay comandos MQTT outbound activos para encender o apagar el Shelly desde backend.
- El pipeline no usa cola intermedia; suficiente para demo y MVP, pero limitado para muchos dispositivos simultaneos.
- La simulacion no publica a Mosquitto: entra directamente por servicios internos para reutilizar persistencia, broadcast y alertas.

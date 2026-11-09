# Anexo C: Ingesta de telemetría asíncrona con Spring Integration MQTT

Este anexo describe cómo Wattimizer recibe telemetría IoT, la transforma en lecturas persistidas y la reenvía al frontend en tiempo real. El código principal está en `MqttConfig`, `DeviceMessageHandler`, `ReadingService`, `TelemetryBroadcaster`, `IotTelemetrySimulationJob` y `SimulatedTelemetryProcessor`.

## 1. Objetivo de la ingesta

El backend necesita aceptar datos energéticos que no nacen de una petición HTTP del usuario. Un enchufe Shelly publica mensajes MQTT cuando cambia el estado o reporta consumo. Por eso se usa Spring Integration MQTT: permite suscribirse al broker, enrutar mensajes por topic y convertir JSON a DTOs Java sin bloquear la API REST.

## 2. Configuración MQTT

Archivo: `config/MqttConfig.java`

La conexión se configura con `DefaultMqttPahoClientFactory` y `MqttConnectOptions`:

- `mqtt.url`
- `mqtt.username`
- `mqtt.password`
- reconexión automática activada
- sesión limpia activada

El adaptador de entrada escucha:

```java
new MqttPahoMessageDrivenChannelAdapter(
    "backend-spring-iot",
    mqttClientFactory(),
    "shellyplugsg3-9070694d3590/#"
);
```

Actualmente el topic está enfocado al Shelly físico usado en desarrollo. El comodín `#` permite recibir subtopics del dispositivo, como eventos RPC y estado del switch.

La calidad de servicio está configurada en `QoS 1`, una decisión razonable para telemetría: se prefiere recibir al menos una vez aunque exista posibilidad de duplicado, antes que perder lecturas por un corte puntual.

## 3. Enrutado de mensajes

`mqttInboundFlow` enruta cada mensaje según el topic recibido:

```java
if (topic != null && topic.endsWith("/events/rpc")) return "EVENTS";
if (topic != null && topic.endsWith("/status/switch:0")) return "STATUS";
return "IGNORE";
```

Después cada rama transforma el JSON:

| Rama | Topic esperado | DTO | Canal |
|---|---|---|---|
| `EVENTS` | termina en `/events/rpc` | `EventsRpc` | `eventsRpcChannel` |
| `STATUS` | termina en `/status/switch:0` | `Status` | `statusChannel` |
| `IGNORE` | cualquier otro | - | `nullChannel` |

Flujo completo:

```mermaid
flowchart TD
    A[Shelly publica MQTT] --> B[Mosquitto]
    B --> C[MqttPahoMessageDrivenChannelAdapter]
    C --> D{Topic}
    D -->|/events/rpc| E[JSON -> EventsRpc]
    D -->|/status/switch:0| F[JSON -> Status]
    D -->|otros| G[nullChannel]
    E --> H[eventsRpcChannel]
    F --> I[statusChannel]
    H --> J[DeviceMessageHandler.handleEventsRpc]
    I --> K[DeviceMessageHandler.handleStatus]
    J --> L[ReadingService]
    K --> L
    L --> M[(readings)]
    M --> N[TelemetryBroadcaster]
    N --> O[/topic/readings/{mac}]
```

## 4. DTOs de telemetría

### 4.1. `EventsRpc`

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventsRpc(
    @JsonProperty("src")
    String source,
    Params params
) {}
```

Representa mensajes RPC de Shelly. Se ignoran campos desconocidos para que el backend no se rompa si el firmware añade datos extra.

### 4.2. `Status`

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public record Status(
    Boolean output,
    @JsonProperty("apower")
    Double activePower,
    @JsonProperty("aenergy")
    ActiveEnergy activeEnergy
) {}
```

Este DTO recoge el estado del interruptor (`output`), potencia activa (`apower`) y energía acumulada (`aenergy`).

## 5. Procesamiento de eventos

Archivo: `services/DeviceMessageHandler.java`

### 5.1. Rama `eventsRpcChannel`

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

Pasos:

1. Recibe `EventsRpc` ya deserializado.
2. Lo convierte a `Reading` con MapStruct dentro de `ReadingService`.
3. Persiste la lectura.
4. Emite la lectura por WebSocket.
5. Comprueba si debe generar alerta por umbral de potencia.

### 5.2. Rama `statusChannel`

```java
@Transactional
@ServiceActivator(inputChannel = "statusChannel")
public void handleStatus(Message<Status> mqttMessage) {
    String topic = mqttMessage.getHeaders().get(MqttHeaders.RECEIVED_TOPIC, String.class);
    String macAddress = (topic != null) ? topic.split("/")[0].split("-")[1] : null;
    DeviceDto deviceDto = deviceService.findByMacAddress(macAddress);

    Status payload = mqttMessage.getPayload();

    Reading reading = readingService.saveEntity(deviceDto, payload);
    broadcaster.broadcast(readingResponseMapper.toDto(reading));
    alertService.checkPowerThreshold(reading);
}
```

Aquí la MAC se extrae del topic, no del cuerpo. Después se busca el dispositivo registrado y se guarda la lectura asociada.

Si la MAC no existe, `DeviceService.findByMacAddress` lanza `EntityNotFoundException`. Esto evita asociar un estado Shelly a un usuario incorrecto.

## 6. Persistencia de lecturas

Archivo: `services/ReadingService.java`

Hay dos caminos:

| Método | Origen | Detalle |
|---|---|---|
| `saveEntity(EventsRpc dto)` | evento RPC | Mapea el DTO a entidad y busca/crea dispositivo por MAC. |
| `saveEntity(DeviceDto deviceDto, Status dto)` | estado del switch | Mapea `Status`, asigna `Instant.now()` y enlaza el dispositivo. |

En `EventsRpc`, si el mapper no consigue una MAC, el servicio puede acabar usando cadena vacía como identificador. Por eso la documentación no debe asumir que todo evento RPC provisiona automáticamente un dispositivo válido; la ruta más controlada para usuario final es `/api/v1/devices/claim`.

La entidad final es:

```java
@Entity
@Table(name = "readings")
@IdClass(ReadingId.class)
public class Reading {
    @Id
    private Instant time;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    private BigDecimal powerW;
    private BigDecimal energyTotalKwh;
    private Boolean isOn;
}
```

La clave `(time, device_id)` permite guardar muchas lecturas por dispositivo sin depender de un id autoincremental, que no aporta información analítica en una serie temporal.

## 7. Reenvío al frontend con STOMP

Archivo: `services/TelemetryBroadcaster.java`

```java
public void broadcast(ReadingResponse readingDto) {
    String destination = "/topic/readings/" + readingDto.macAddress();
    messagingTemplate.convertAndSend(destination, readingDto);
}
```

La lectura persistida se convierte a `ReadingResponse` y se publica en un topic STOMP específico de la MAC. Angular escucha ese topic desde `WebsocketService.watchReadings(macAddress)`.

La configuración STOMP está en `StompWebSocketConfig`:

- endpoint de handshake: `/ws-iot`
- broker simple: `/topic`
- prefijo de mensajes de aplicación: `/app`
- orígenes permitidos desde `app.cors.allowed-origins`

## 8. Simulación de telemetría

Los cambios recientes incorporan simuladores para que Wattimizer pueda demostrarse sin enchufe físico.

### 8.1. Job programado

Archivo: `IotTelemetrySimulationJob.java`

```java
@Scheduled(fixedRateString = "${simulation.interval-ms:5000}")
public void publishSimulatedTelemetry() {
    if (!simulationProperties.enabled()) {
        return;
    }

    Instant tickStart = Instant.now();
    long intervalMs = simulationProperties.intervalMs();
    List<Device> simulatedDevices = deviceRepository.findBySimulatedTrue();

    for (Device device : simulatedDevices) {
        try {
            telemetryProcessor.processDevice(device, tickStart, intervalMs);
        } catch (Exception ex) {
            log.warn("Fallo al simular telemetría del dispositivo id={} mac={}: {}",
                    device.getId(), device.getMacAddress(), ex.getMessage());
        }
    }
}
```

Decisiones relevantes:

- La simulación se activa con `simulation.enabled`.
- El intervalo por defecto es `5000 ms`.
- Cada dispositivo se procesa de forma aislada; si uno falla, el job continúa con los demás.

### 8.2. Procesador por dispositivo

Archivo: `SimulatedTelemetryProcessor.java`

El procesador crea una transacción nueva por dispositivo:

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void processDevice(Device device, Instant tickStart, long intervalMs) { ... }
```

Esto evita que un fallo puntual revierta lecturas generadas correctamente para otros simuladores en el mismo tick.

Pasos:

1. Lee `simulationProfile` del dispositivo.
2. Si `isOn` es `false`, genera `0 W` y mantiene el contador de kWh.
3. Si está encendido, calcula potencia con `SimulationProfileRegistry`.
4. Lee el último `energyTotalKwh` del dispositivo.
5. Calcula el siguiente acumulado con:

```text
kWh_nuevo = kWh_anterior + (W * segundos) / 1000 / 3600
```

6. Guarda la lectura como si viniera de un dispositivo real.
7. La emite por WebSocket y comprueba alertas.

### 8.3. Perfiles de simulación

Archivo: `SimulationProfileRegistry.java`

| Perfil | Intención |
|---|---|
| `SINE_WAVE` | Onda de prueba estable para demo visual. |
| `OVEN` | Consumo alto típico de horno. |
| `WASHING_MACHINE` | Patrón variable de lavadora. |
| `TELEVISION` | Consumo medio estable. |
| `FAN` | Consumo bajo/medio. |
| `DESKTOP_PC` | Consumo de ordenador de sobremesa. |
| `FRIDGE` | Ciclos de nevera. |
| `STANDBY` | Consumo fantasma bajo pero persistente. |
| `CONSTANT_HIGH_LOAD` | Carga alta constante para probar alertas. |

Si el perfil llega nulo o no existe calculadora, el registro cae a `SINE_WAVE`. Es una decisión defensiva para que una migración o dato incompleto no pare por completo la demo.

## 9. Flujo físico y flujo simulado

| Aspecto | Dispositivo físico | Dispositivo simulado |
|---|---|---|
| Origen | Shelly Plug S vía Mosquitto. | Job interno programado. |
| Entrada | MQTT JSON. | Cálculo Java por perfil. |
| Identificador | MAC real del Shelly. | MAC sintética `SIM000000001`, etc. |
| Persistencia | `ReadingService.saveEntity(...)`. | `ReadingService.saveSimulatedReading(...)`. |
| Tiempo real | `TelemetryBroadcaster`. | `TelemetryBroadcaster`. |
| Alertas | `AlertService.checkPowerThreshold(...)`. | `AlertService.checkPowerThreshold(...)`. |

La salida se unifica en `readings` y WebSocket. Esto es importante porque el frontend no necesita saber si una lectura viene de hardware real o simulación.

## 10. Configuración por entorno

En `application.properties`:

```properties
mqtt.url=${MQTT_URL:tcp://localhost:1883}
mqtt.username=${MQTT_USER:gateway-service}
mqtt.password=${MQTT_PASSWORD:...}
simulation.enabled=${SIMULATION_ENABLED:true}
simulation.interval-ms=${SIMULATION_INTERVAL_MS:5000}
```

En `docker-compose.yml`:

- Mosquitto expone `1883` para el Shelly físico.
- El backend usa `MQTT_URL=tcp://mosquitto:1883`.
- `SIMULATION_ENABLED` se deja activado por defecto en producción para el modo demo.

El compose incluye una nota de deuda técnica: MQTT en `1883` viaja en texto plano. La mejora prevista es TLS en `8883` o una VPN cuando el hardware lo permita.

## 11. Limitaciones actuales

- La suscripción MQTT está fijada al topic del Shelly de desarrollo (`shellyplugsg3-9070694d3590/#`).
- No hay ACL dinámica por dispositivo dentro del broker.
- No se documenta una política de reintentos propia aparte de la reconexión automática del cliente Paho.
- Los duplicados potenciales por QoS 1 se mitigan parcialmente en Angular deduplicando por `time`, pero no existe una tabla de mensajes MQTT procesados.

Estas limitaciones no impiden el MVP, pero son puntos claros para una evolución de producción más robusta.

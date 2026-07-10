# Anexo C. Ingesta de telemetría asíncrona con Spring Integration MQTT

Este anexo explica cómo Wattimizer recibe telemetría de dispositivos IoT. Hay dos fuentes de datos:

1. **Dispositivo físico Shelly Plug S G3**, que publica mensajes MQTT en Mosquitto.
2. **Simuladores internos**, generados por un job programado de Spring para poder hacer demos sin hardware.

Ambas vías terminan en la misma salida: una lectura `Reading` persistida, una emisión WebSocket para el dashboard y una comprobación de alerta por sobrepotencia.

## C.1. Configuración MQTT

La clase `MqttConfig` está anotada con `@Configuration` y `@EnableIntegration`. Define la conexión Paho, los canales internos y el flujo de entrada.

Propiedades reales:

```properties
mqtt.url=${MQTT_URL:tcp://localhost:1883}
mqtt.username=${MQTT_USER:gateway-service}
mqtt.password=${MQTT_PASSWORD:s3cr3t}
```

En Docker, el backend usa:

```yaml
MQTT_URL: tcp://mosquitto:1883
MQTT_USER: ${PROD_MQTT_USER}
MQTT_PASSWORD: ${PROD_MQTT_PASSWORD}
```

El broker Mosquitto se publica en el puerto `1883`. El propio `docker-compose.yml` marca este punto como deuda de seguridad porque MQTT circula en texto plano. Para producción real sería mejor usar TLS/8883 o una VPN.

## C.2. Adaptador de entrada

El bean `mqttInbound()` crea un `MqttPahoMessageDrivenChannelAdapter`:

```java
new MqttPahoMessageDrivenChannelAdapter(
    "backend-spring-iot",
    mqttClientFactory(),
    "shellyplugsg3-9070694d3590/#"
);
```

| Propiedad | Valor |
|---|---|
| Client ID backend | `backend-spring-iot` |
| Topic suscrito | `shellyplugsg3-9070694d3590/#` |
| QoS | `1` |
| Reconexión | `setAutomaticReconnect(true)` |
| Sesión MQTT | `setCleanSession(true)` |

El topic está hardcodeado para un Shelly concreto. Esto es suficiente para el prototipo, pero limita el alta dinámica de hardware físico. Los dispositivos simulados no pasan por MQTT y, por tanto, no tienen esta limitación.

## C.3. Canales y enrutamiento

`MqttConfig` define dos `DirectChannel`:

- `eventsRpcChannel`
- `statusChannel`

`DirectChannel` ejecuta el handler en el hilo de recepción. Es simple y suficiente para el volumen del proyecto, aunque no introduce cola ni backpressure.

El `IntegrationFlow` decide la rama mirando `MqttHeaders.RECEIVED_TOPIC`:

```java
if (topic != null && topic.endsWith("/events/rpc")) return "EVENTS";
if (topic != null && topic.endsWith("/status/switch:0")) return "STATUS";
return "IGNORE";
```

| Sufijo de topic | Transformación | Canal |
|---|---|---|
| `/events/rpc` | JSON a `EventsRpc` | `eventsRpcChannel` |
| `/status/switch:0` | JSON a `Status` | `statusChannel` |
| Cualquier otro | Descartado | `nullChannel` |

Esta separación existe porque Shelly envía mensajes con estructuras distintas. Los eventos RPC aportan timestamp de origen y energía/potencia dentro de `params`, mientras que el status aporta el estado `output` del relé.

## C.4. DTOs MQTT

### `EventsRpc`

Estructura lógica:

```text
EventsRpc
├── src
└── params
    ├── ts
    └── switch:0
        ├── apower
        └── aenergy.total
```

Campos principales:

- `src`: cadena tipo `shellyplugsg3-9070694d3590`.
- `params.ts`: timestamp en epoch seconds.
- `switch:0.apower`: potencia activa en vatios.
- `switch:0.aenergy.total`: energía acumulada en Wh.

### `Status`

Estructura lógica:

```text
Status
├── output
├── apower
└── aenergy.total
```

Campos principales:

- `output`: indica si el relé está encendido.
- `apower`: potencia activa.
- `aenergy.total`: energía acumulada.

Los DTOs usan `@JsonIgnoreProperties(ignoreUnknown = true)`, lo que permite tolerar campos adicionales del firmware Shelly sin romper la deserialización.

## C.5. Entrada a servicios con `DeviceMessageHandler`

`DeviceMessageHandler` contiene dos métodos activados por Spring Integration:

```java
@ServiceActivator(inputChannel = "eventsRpcChannel")
public void handleEventsRpc(Message<EventsRpc> mqttMessage) { ... }
```

```java
@ServiceActivator(inputChannel = "statusChannel")
public void handleStatus(Message<Status> mqttMessage) { ... }
```

### Rama `EVENTS`

Flujo:

1. Recibe `EventsRpc`.
2. `ReadingService.saveEntity(payload)` mapea y persiste la lectura.
3. `TelemetryBroadcaster.broadcast(...)` emite al frontend.
4. `AlertService.checkPowerThreshold(reading)` comprueba maxímetro.

En esta rama, la MAC se extrae del campo `src`. Si el dispositivo no existe, `ReadingService` lo auto-provisiona con nombre `Nuevo Enchufe {mac}` y sin usuario asociado.

### Rama `STATUS`

Flujo:

1. Lee el topic recibido desde `MqttHeaders.RECEIVED_TOPIC`.
2. Extrae la MAC del primer segmento del topic.
3. Busca el dispositivo con `DeviceService.findByMacAddress`.
4. Mapea `Status` a `Reading`, usando `Instant.now()` como tiempo.
5. Emite WebSocket y comprueba alertas.

En esta rama el dispositivo debe existir previamente. Si no existe, `findByMacAddress` lanza `EntityNotFoundException`.

## C.6. Mapeo a `Reading`

La entidad persistida contiene:

| Campo | Origen MQTT |
|---|---|
| `time` | `params.ts` en `EventsRpc` o `Instant.now()` en `Status`. |
| `device` | MAC extraída del payload/topic. |
| `powerW` | `apower`. |
| `energyTotalKwh` | `aenergy.total / 1000`, porque Shelly envía Wh. |
| `isOn` | Solo disponible de forma fiable en `Status.output`. |

La clave primaria de `Reading` es compuesta: `time + device_id`. Por eso en simulación se añade un pequeño desfase por dispositivo para evitar colisiones cuando varios simuladores generan lectura en el mismo tick.

## C.7. Broadcast STOMP

`TelemetryBroadcaster` usa `SimpMessagingTemplate`:

```java
public void broadcast(ReadingResponse readingDto) {
    String destination = "/topic/readings/" + readingDto.macAddress();
    messagingTemplate.convertAndSend(destination, readingDto);
}
```

Para alertas:

```java
public void broadcast(AlertDto alertDto) {
    String destination = "/topic/alerts/" + alertDto.username();
    messagingTemplate.convertAndSend(destination, alertDto);
}
```

El frontend consume solo:

```text
/topic/readings/{macAddress}
```

La emisión de alertas por WebSocket ya existe en backend, pero la interfaz actual las lista por REST.

## C.8. Flujo completo de MQTT físico

```mermaid
flowchart TD
    Shelly[Shelly Plug S G3] -->|MQTT QoS 1| Mosquitto[Mosquitto]
    Mosquitto --> Adapter[MqttPahoMessageDrivenChannelAdapter]
    Adapter --> Router{Topic recibido}
    Router -->|/events/rpc| Events[JSON a EventsRpc]
    Router -->|/status/switch:0| Status[JSON a Status]
    Router -->|otro| Null[nullChannel]
    Events --> Handler1[DeviceMessageHandler.handleEventsRpc]
    Status --> Handler2[DeviceMessageHandler.handleStatus]
    Handler1 --> Reading[ReadingService]
    Handler2 --> Reading
    Reading --> DB[(readings)]
    Reading --> WS[TelemetryBroadcaster]
    Reading --> Alert[AlertService.checkPowerThreshold]
    WS --> Topic[/topic/readings/{mac}/]
    Alert --> Alerts[(alerts)]
```

## C.9. Simulación de telemetría

La simulación se controla con:

```properties
simulation.enabled=${SIMULATION_ENABLED:true}
simulation.interval-ms=${SIMULATION_INTERVAL_MS:5000}
```

`IotTelemetrySimulationJob` se ejecuta con:

```java
@Scheduled(fixedRateString = "${simulation.interval-ms:5000}")
```

Si `simulation.enabled` es `false`, no genera lecturas. Si está activo:

1. Busca dispositivos con `DeviceRepository.findBySimulatedTrue()`.
2. Recorre cada dispositivo.
3. Llama a `SimulatedTelemetryProcessor.processDevice`.
4. Si falla un dispositivo, registra warning y continúa con el siguiente.

La transacción del procesador usa `Propagation.REQUIRES_NEW`, de forma que un fallo de un simulador no revierte el resto del tick.

## C.10. Perfiles de simulación

`SimulationProfile` contiene:

- `SINE_WAVE`
- `OVEN`
- `WASHING_MACHINE`
- `TELEVISION`
- `FAN`
- `DESKTOP_PC`
- `FRIDGE`
- `STANDBY`
- `CONSTANT_HIGH_LOAD`

`SimulationProfileRegistry` resuelve el perfil hacia un calculador de potencia. Si el perfil es `null`, usa `SINE_WAVE` como fallback. Algunos ejemplos:

| Perfil | Uso en la demo |
|---|---|
| `SINE_WAVE` | Carga de prueba estable con oscilación. |
| `OVEN` | Pico alto de precalentamiento y fase de cocción. |
| `WASHING_MACHINE` | Ciclo con fases de lavado y centrifugado. |
| `FRIDGE` | Consumo bajo con arranques de compresor. |
| `STANDBY` | Consumo fantasma continuo. |
| `CONSTANT_HIGH_LOAD` | Carga fija alta para probar alertas de maxímetro. |

## C.11. Cálculo de energía simulada

`SimulatedTelemetryProcessor` calcula la energía acumulada así:

```text
nextKwh = previousKwh + (powerW / 1000) * (intervalSeconds / 3600)
```

Si el dispositivo está apagado, la potencia es `0` y el odómetro no avanza. Después se persiste la lectura y se ejecuta el mismo pipeline que con MQTT físico:

```text
Reading -> WebSocket -> AlertService
```

Esta convergencia es una decisión acertada: el dashboard y las alertas no necesitan saber si una lectura viene de hardware real o de simulación.

## C.12. Limitaciones actuales

- La suscripción MQTT está fijada a `shellyplugsg3-9070694d3590/#`.
- El canal interno es síncrono (`DirectChannel`), sin cola ni procesamiento paralelo.
- La rama `EVENTS` no informa de `isOn`; la rama `STATUS` sí.
- El broker usa puerto 1883 sin TLS.
- El frontend todavía no consume alertas en tiempo real.

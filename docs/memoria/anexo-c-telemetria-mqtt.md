# Anexo C - Ingesta de telemetría MQTT con Spring Integration

## 1. Objetivo del módulo

El módulo de telemetría recibe datos eléctricos de dispositivos Shelly mediante MQTT, los convierte a entidades del dominio, los guarda en la tabla `readings` y los reenvía al frontend por WebSocket STOMP.

El código principal está en:

- `config/MqttConfig.java`
- `services/DeviceMessageHandler.java`
- `services/ReadingService.java`
- `services/TelemetryBroadcaster.java`
- `services/AlertService.java`
- DTOs `EventsRpc`, `Status`, `Params`, `Switch` y `ActiveEnergy`

## 2. Arquitectura general

```mermaid
flowchart LR
  Shelly[Shelly Plug S G3] -->|MQTT| Broker[Eclipse Mosquitto]
  Broker --> Adapter[MqttPahoMessageDrivenChannelAdapter]
  Adapter --> Router[IntegrationFlow.route por topic]
  Router -->|/events/rpc| Events[EventsRpc]
  Router -->|/status/switch:0| Status[Status]
  Router -->|otros| Null[nullChannel]
  Events --> Handler[DeviceMessageHandler]
  Status --> Handler
  Handler --> ReadingService[ReadingService]
  ReadingService --> DB[(readings)]
  Handler --> Broadcaster[TelemetryBroadcaster]
  Broadcaster --> STOMP[/topic/readings/{mac}/]
  Handler --> Alerts[AlertService]
```

La idea es que el backend actúe como puente entre el mundo IoT y la aplicación web. MQTT se usa para recibir datos desde dispositivos, REST para consultar histórico y STOMP para pintar cambios en directo.

## 3. Configuración MQTT

**Archivo:** `config/MqttConfig.java`

Propiedades inyectadas:

| Propiedad | Uso |
| --- | --- |
| `mqtt.url` | URL del broker, por ejemplo `tcp://localhost:1883` |
| `mqtt.username` | Usuario MQTT |
| `mqtt.password` | Contraseña MQTT |

Cliente:

```java
new MqttPahoMessageDrivenChannelAdapter(
    "backend-spring-iot",
    mqttClientFactory(),
    "shellyplugsg3-9070694d3590/#"
);
```

Configuración de conexión:

| Opción | Valor |
| --- | --- |
| Client ID | `backend-spring-iot` |
| Suscripción | `shellyplugsg3-9070694d3590/#` |
| QoS | `1` |
| Reconexión automática | `true` |
| Clean session | `true` |

Actualmente la suscripción está fijada a un dispositivo Shelly concreto. Para un sistema multi-dispositivo real, esta parte debería parametrizarse o ampliarse a un wildcard más general.

## 4. Canales y router de Spring Integration

`MqttConfig` declara dos canales:

```java
@Bean
public MessageChannel eventsRpcChannel() {
    return new DirectChannel();
}

@Bean
MessageChannel statusChannel() {
    return new DirectChannel();
}
```

Son `DirectChannel`, por lo que el procesamiento ocurre de forma síncrona en el flujo de Spring Integration. El router decide la rama según el tópico recibido:

```java
if (topic != null && topic.endsWith("/events/rpc")) return "EVENTS";
if (topic != null && topic.endsWith("/status/switch:0")) return "STATUS";
return "IGNORE";
```

Ramas:

| Rama | Transformación | Canal final |
| --- | --- | --- |
| `EVENTS` | JSON a `EventsRpc` | `eventsRpcChannel` |
| `STATUS` | JSON a `Status` | `statusChannel` |
| `IGNORE` | Sin transformación | `nullChannel` |

Los tópicos no reconocidos se descartan silenciosamente. Es útil para no romper el backend con mensajes auxiliares del dispositivo, pero dificulta depurar si se esperaba procesar un tópico nuevo.

## 5. Tópicos MQTT reconocidos

| Tópico | DTO | Uso |
| --- | --- | --- |
| `shellyplugsg3-9070694d3590/events/rpc` | `EventsRpc` | Evento RPC completo emitido por Shelly |
| `shellyplugsg3-9070694d3590/status/switch:0` | `Status` | Estado del relé y consumo actual |

En la rama `status`, la MAC se extrae desde el tópico:

```java
topic.split("/")[0].split("-")[1]
```

Para `shellyplugsg3-9070694d3590/status/switch:0`, el resultado es `9070694d3590`.

## 6. DTO `EventsRpc`

**Archivo:** `dtos/EventsRpc.java`

Payload esperado:

```json
{
  "src": "shellyplugsg3-9070694d3590",
  "params": {
    "ts": 1720123456.0,
    "switch:0": {
      "apower": 1250.5,
      "aenergy": {
        "total": 1234567.8
      }
    }
  }
}
```

Campos usados:

| JSON | Campo Java | Conversión |
| --- | --- | --- |
| `src` | `source` | Se usa para obtener la MAC |
| `params.ts` | `timestamp` | Epoch a `Instant` |
| `params.switch:0.apower` | `activePower` | Vatios a `power_w` |
| `params.switch:0.aenergy.total` | `total` | Wh a kWh dividiendo entre 1000 |

`EventsRpc` permite auto-provisionar el dispositivo si no existe todavía. Esto es práctico para la primera llegada de telemetría desde el Shelly.

## 7. DTO `Status`

**Archivo:** `dtos/Status.java`

Payload esperado:

```json
{
  "output": true,
  "apower": 850.0,
  "aenergy": {
    "total": 987654.3
  }
}
```

Campos usados:

| JSON | Campo Java | Conversión |
| --- | --- | --- |
| `output` | `Boolean` | Estado lógico `is_on` |
| `apower` | `activePower` | Vatios |
| `aenergy.total` | `total` | Wh a kWh |

A diferencia de `EventsRpc`, este payload no trae timestamp propio. El backend usa `Instant.now()` al guardar la lectura.

## 8. Handler de mensajes

**Archivo:** `services/DeviceMessageHandler.java`

Métodos:

```java
@ServiceActivator(inputChannel = "eventsRpcChannel")
public void handleEventsRpc(EventsRpc eventsRpc) { ... }

@ServiceActivator(inputChannel = "statusChannel")
public void handleStatus(Status status, @Header(MqttHeaders.RECEIVED_TOPIC) String topic) { ... }
```

Secuencia de `events/rpc`:

1. Convertir el DTO Shelly en lectura.
2. Guardar en base de datos mediante `ReadingService.saveEntity(eventsRpc)`.
3. Enviar la lectura por WebSocket con `TelemetryBroadcaster`.
4. Comprobar umbral de potencia con `AlertService`.

Secuencia de `status/switch:0`:

1. Extraer MAC desde el tópico.
2. Buscar el dispositivo con `DeviceService.findByMacAddress`.
3. Guardar lectura con `ReadingService.saveEntity(deviceDto, status)`.
4. Emitir la lectura por WebSocket.
5. Comprobar alerta de sobrepotencia.

## 9. Persistencia de lecturas

La entidad `Reading` representa una muestra energética:

| Columna | Tipo conceptual | Significado |
| --- | --- | --- |
| `time` | `Instant` | Momento de la lectura |
| `device_id` | FK a `devices` | Dispositivo que generó la lectura |
| `power_w` | decimal | Potencia activa instantánea en vatios |
| `energy_total_kwh` | decimal | Odómetro energético acumulado |
| `is_on` | boolean | Estado lógico del relé |

La clave primaria es compuesta:

```text
(time, device_id)
```

Esta clave impide dos lecturas para el mismo dispositivo en el mismo instante exacto. En simulación se añade un pequeño desfase por dispositivo para reducir colisiones cuando varios simuladores generan lectura a la vez.

## 10. Broadcast en tiempo real

**Archivo:** `services/TelemetryBroadcaster.java`

El backend publica lecturas en:

```text
/topic/readings/{macAddress}
```

El frontend se suscribe con `WebsocketService.watchReadings(macAddress)`. El payload enviado coincide con `ReadingResponse`:

```json
{
  "time": "2026-07-04T22:01:00Z",
  "macAddress": "9070694d3590",
  "powerW": 1250.50,
  "energyTotalKwh": 1234.5678,
  "isOn": true
}
```

Esto separa bien responsabilidades:

- MQTT recibe mensajes de dispositivos.
- La base de datos guarda histórico.
- STOMP mantiene la UI viva sin polling.

## 11. Alertas tras la ingesta

Después de guardar cada lectura, `AlertService.checkPowerThreshold` compara la potencia activa con la potencia contratada del periodo aplicable.

Flujo:

1. Resolver el periodo P1-P6 con `CalendarResolverService`.
2. Buscar la potencia contratada en `tariff_contracted_powers`.
3. Convertir `powerW` a kW.
4. Si supera lo contratado, crear una alerta `OVERPOWER`.
5. Publicar la alerta en `/topic/alerts/{username}`.

La alerta depende de que el dispositivo esté asociado a un usuario con tarifa configurada. Si no hay contrato, no hay base suficiente para decidir si hay exceso.

## 12. Simulación IoT

Además del flujo MQTT real, el backend incluye simulación:

| Clase | Función |
| --- | --- |
| `IotTelemetrySimulationJob` | Job programado con `@Scheduled` |
| `SimulatedTelemetryProcessor` | Procesa cada dispositivo simulado en transacción independiente |
| `SimulationProfileRegistry` | Asocia perfil con calculador |
| `simulation/*PowerCalculator` | Calcula potencia según perfil |

Perfiles disponibles:

- `SINE_WAVE`
- `OVEN`
- `WASHING_MACHINE`
- `TELEVISION`
- `FAN`
- `DESKTOP_PC`
- `FRIDGE`
- `STANDBY`
- `CONSTANT_HIGH_LOAD`

El flujo simulado usa la misma salida que MQTT: guarda en `readings`, emite WebSocket y comprueba alertas. Esto permite demostrar la aplicación sin hardware físico y evita duplicar lógica en el frontend.

## 13. Tratamiento de errores y limitaciones

| Situación | Comportamiento actual |
| --- | --- |
| Tópico no reconocido | Se envía a `nullChannel` |
| JSON inválido | No hay `errorChannel` específico; la excepción se propaga en el flujo |
| Dispositivo desconocido en `events/rpc` | Puede crearse automáticamente |
| Dispositivo desconocido en `status/switch:0` | Falla al no encontrar MAC |
| Broker caído temporalmente | Paho intenta reconexión automática |
| Duplicado `(time, device_id)` | Excepción de integridad de base de datos |

Limitaciones técnicas:

- La suscripción MQTT está hardcodeada a un Shelly concreto.
- Mosquitto está configurado sin TLS en el entorno Docker actual.
- No existe un canal de errores específico para registrar payloads mal formados.
- La rama `status` no autoprovisiona dispositivos.

## 14. Decisiones de diseño

- **Spring Integration MQTT:** permite declarar un pipeline claro: adapter, router, transformadores y canales.
- **DTOs separados por tipo de tópico:** evita forzar un único modelo para payloads distintos.
- **Persistencia antes de broadcast:** si una lectura se ve en pantalla, también queda guardada como histórico.
- **Mismo pipeline para simulación y MQTT:** reduce diferencias entre demo y uso real.
- **Alertas post-ingesta:** la alerta se genera en el backend, no en Angular, porque depende de tarifa, calendario y potencia contratada.

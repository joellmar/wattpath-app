# Anexo 03. Ingesta MQTT con Spring Integration

## 1. Objetivo del módulo MQTT

La ingesta MQTT permite que Wattimizer reciba telemetría enviada por enchufes inteligentes Shelly. A diferencia de las operaciones REST, aquí el backend no espera una petición del frontend: escucha un broker Mosquitto, transforma los mensajes entrantes y los convierte en lecturas persistentes.

El flujo implementado es:

```text
Shelly Plug -> Mosquitto -> Spring Integration MQTT -> DTO MQTT -> Mapper -> Reading -> PostgreSQL/TimescaleDB -> WebSocket/STOMP -> Angular
```

Esta arquitectura separa bien dos responsabilidades:

- MQTT se encarga de la entrada asíncrona de datos desde dispositivos físicos.
- WebSocket se encarga de reenviar al navegador las lecturas ya normalizadas.

## 2. Broker Mosquitto

El broker se define en `docker-compose.yml`:

```yaml
mosquitto:
  image: eclipse-mosquitto:2.1.2-alpine
  container_name: broker_mqtt
  restart: always
  ports:
    - "1883:1883"
  volumes:
    - ./mosquitto/config/mosquitto.conf:/mosquitto/config/mosquitto.conf
    - ./mosquitto/config/password_file:/mosquitto/config/password_file
```

La configuración real de Mosquitto está en `mosquitto/config/mosquitto.conf`:

```conf
allow_anonymous false
password_file /mosquitto/config/password_file
listener 1883 0.0.0.0
```

Por tanto, el broker no permite conexiones anónimas. El backend recibe las credenciales por variables de entorno:

```yaml
- MQTT_URL=tcp://mosquitto:1883
- MQTT_USER=${PROD_MQTT_USER}
- MQTT_PASSWORD=${PROD_MQTT_PASSWORD}
```

En desarrollo local, `application.properties` define valores por defecto:

```properties
mqtt.url=tcp://localhost:1883
mqtt.username=${MQTT_USER:gateway-service}
mqtt.password=${MQTT_PASSWORD:s3cr3t}
```

## 3. Configuración de Spring Integration MQTT

Archivo: `backend/src/main/java/com/joselumartos/jwtauthbackenddemo/config/MqttConfig.java`

La clase `MqttConfig` habilita Spring Integration con `@EnableIntegration` y crea los beans necesarios para escuchar el broker.

### 3.1 Factoría de conexión

```java
options.setServerURIs(new String[] { mqttUrl });
options.setUserName(mqttUsername);
options.setPassword(mqttPassword.toCharArray());
options.setAutomaticReconnect(true);
options.setCleanSession(true);
```

Decisiones relevantes:

- `automaticReconnect=true`: el backend intenta recuperar la conexión si el broker cae o se reinicia.
- `cleanSession=true`: no se conservan suscripciones pendientes entre sesiones MQTT.
- las credenciales se inyectan desde propiedades, evitando dejarlas fijas en el código.

### 3.2 Canales internos

La configuración declara dos canales de Spring Integration:

```java
public MessageChannel eventsRpcChannel() {
    return new DirectChannel();
}

@Bean MessageChannel statusChannel() {
    return new DirectChannel();
}
```

Se usan dos canales porque Shelly puede enviar mensajes con estructuras JSON distintas:

- eventos RPC;
- estado directo del interruptor `switch:0`.

Separarlos evita que un mismo handler tenga que interpretar manualmente todos los formatos.

### 3.3 Adaptador MQTT entrante

```java
new MqttPahoMessageDrivenChannelAdapter(
    "backend-spring-iot",
    mqttClientFactory(),
    "shellyplugsg3-9070694d3590/#"
);
adapter.setQos(1);
```

El backend se suscribe al árbol:

```text
shellyplugsg3-9070694d3590/#
```

El comodín `#` permite recibir subtopics del dispositivo. El QoS 1 indica entrega "al menos una vez", adecuada para telemetría donde es preferible recibir un mensaje duplicado antes que perderlo sin confirmación.

## 4. Enrutado de mensajes por topic

El bean `mqttInboundFlow` enruta cada mensaje según el topic recibido en la cabecera `MqttHeaders.RECEIVED_TOPIC`:

```java
if (topic != null && topic.endsWith("/events/rpc")) return "EVENTS";
if (topic != null && topic.endsWith("/status/switch:0")) return "STATUS";
return "IGNORE";
```

Rutas implementadas:

| Sufijo del topic | Ruta interna | Transformación | Canal destino |
| --- | --- | --- | --- |
| `/events/rpc` | `EVENTS` | JSON -> `EventsRpc` | `eventsRpcChannel` |
| `/status/switch:0` | `STATUS` | JSON -> `Status` | `statusChannel` |
| Cualquier otro | `IGNORE` | Ninguna | `nullChannel` |

El uso de `Transformers.fromJson(...)` hace que el parseo se realice antes de llegar al servicio de negocio. Así, `DeviceMessageHandler` ya recibe DTOs Java y no cadenas JSON sin estructura.

## 5. DTOs MQTT

Los DTOs se encuentran en `backend/src/main/java/com/joselumartos/jwtauthbackenddemo/dtos`.

### 5.1 Evento RPC

```java
public record EventsRpc(
        @JsonProperty("src")
        String source,
        Params params
) {}

public record Params(
        @JsonProperty("ts")
        Double timestamp,
        @JsonProperty("switch:0")
        Switch switchData
) {}

public record Switch(
    @JsonProperty("aenergy")
    ActiveEnergy activeEnergy,
    @JsonProperty("apower")
    Double activePower
) {}
```

Este formato representa mensajes donde Shelly envía `src`, una marca temporal `ts` y los datos del interruptor `switch:0`.

### 5.2 Estado directo

```java
public record Status(
        Boolean output,
        @JsonProperty("apower")
        Double activePower,
        @JsonProperty("aenergy")
        ActiveEnergy activeEnergy
) {}
```

`output` se usa como estado encendido/apagado, `apower` como potencia activa y `aenergy.total` como contador energético acumulado.

### 5.3 Energía activa

```java
public record ActiveEnergy(Double total) {}
```

El valor `total` llega en Wh desde Shelly y se convierte a kWh antes de persistirse.

## 6. Handlers de ingesta

Archivo: `backend/src/main/java/com/joselumartos/jwtauthbackenddemo/services/DeviceMessageHandler.java`

La clase tiene dos métodos activados por canales:

```java
@ServiceActivator(inputChannel = "eventsRpcChannel")
public void handleEventsRpc(Message<EventsRpc> mqttMessage)
```

```java
@ServiceActivator(inputChannel = "statusChannel")
public void handleStatus(Message<Status> mqttMessage)
```

Ambos métodos siguen el mismo patrón:

1. leer el payload tipado;
2. guardar una entidad `Reading`;
3. convertirla a `ReadingResponse`;
4. emitirla por WebSocket;
5. comprobar si genera alerta de potencia.

### 6.1 Flujo de `events/rpc`

```java
EventsRpc payload = mqttMessage.getPayload();
Reading reading = readingService.saveEntity(payload);
broadcaster.broadcast(readingResponseMapper.toDto(reading));
alertService.checkPowerThreshold(reading);
```

En este caso el mapper intenta resolver el dispositivo a partir de `source`, con un formato como:

```text
shellyplugsg3-9070694d3590
```

`EventsRpcMapper.mapSourceToDevice` extrae la parte posterior al último guion y busca esa MAC en `DeviceRepository`. Un detalle importante del código actual es que el mapper no crea el dispositivo por sí mismo: si no encuentra la MAC, devuelve `null`.

### 6.2 Flujo de `status/switch:0`

```java
String topic = mqttMessage.getHeaders().get(MqttHeaders.RECEIVED_TOPIC, String.class);
String macAddress = (topic != null) ? topic.split("/")[0].split("-")[1] : null;
DeviceDto deviceDto = deviceService.findByMacAddress(macAddress);

Status payload = mqttMessage.getPayload();
Reading reading = readingService.saveEntity(deviceDto, payload);
```

Aquí la MAC no se toma del JSON, sino del topic MQTT. Después se busca el dispositivo y se guarda la lectura con la hora actual del servidor (`Instant.now()`).

## 7. Mapeo a entidad `Reading`

### 7.1 Mapper de eventos RPC

Archivo: `EventsRpcMapper.java`

```java
@Mapping(source = "params.timestamp", target = "time", qualifiedByName = "doubleToInstant")
@Mapping(source = "source", target = "device", qualifiedByName = "mapSourceToDevice")
@Mapping(source = "params.switchData.activePower", target = "powerW")
@Mapping(source = "params.switchData.activeEnergy.total", target = "energyTotalKwh", qualifiedByName = "toKwh")
@Mapping(target = "isOn", ignore = true)
public abstract Reading toEntity(EventsRpc dto);
```

Conversión energética:

```java
protected BigDecimal toKwh(Double energyWh) {
    return (energyWh != null)
            ? BigDecimal.valueOf(energyWh).divide(BigDecimal.valueOf(1000))
            : null;
}
```

Esta conversión es necesaria porque Shelly informa energía acumulada en Wh, mientras que la base de datos guarda `energyTotalKwh`.

La resolución de dispositivo queda limitada a dispositivos ya existentes:

```java
protected Device mapSourceToDevice(String source) {
    if (source == null || !source.contains("-")) return null;

    String mac = source.substring(source.lastIndexOf("-") + 1);
    return deviceRepository.findByMacAddress(mac).orElse(null);
}
```

Por tanto, el flujo real no registra una MAC nueva directamente desde `source` dentro del mapper.

### 7.2 Mapper de estado

Archivo: `StatusMapper.java`

```java
@Mapping(target = "time", ignore = true)
@Mapping(target = "device", ignore = true)
@Mapping(source = "activePower", target = "powerW")
@Mapping(source = "activeEnergy.total", target = "energyTotalKwh", qualifiedByName = "toKwh")
@Mapping(source = "output", target = "isOn")
Reading toEntity(Status dto);
```

El mapper ignora `time` y `device` porque esos datos se completan en `ReadingService.saveEntity(DeviceDto, Status)`.

## 8. Persistencia de lecturas

Archivo: `backend/src/main/java/com/joselumartos/jwtauthbackenddemo/services/ReadingService.java`

### 8.1 Guardado desde `EventsRpc`

```java
Reading reading = eventsRpcMapper.toEntity(dto);

String macAddress = (reading.getDevice() == null || reading.getDevice().getMacAddress() == null)
        ? ""
        : reading.getDevice().getMacAddress();

Device managedDevice = deviceRepository.findByMacAddress(macAddress)
        .orElseGet(() -> {
            Device newDevice = new Device();
            newDevice.setMacAddress(macAddress);
            newDevice.setName("Nuevo Enchufe " + macAddress);
            newDevice.setIsOn(true);
            return deviceRepository.save(newDevice);
        });

reading.setDevice(managedDevice);
return readingRepository.save(reading);
```

La decisión importante es que una lectura MQTT puede crear un dispositivo si no se encuentra uno gestionado, pero el código actual solo conserva la MAC cuando `EventsRpcMapper` ya ha resuelto un `Device`. Si el `source` no corresponde a un dispositivo existente, `ReadingService` usa una cadena vacía como MAC. Es una limitación real de la implementación actual y conviene tenerla localizada si se quiere mejorar la ingesta automática de nuevos enchufes.

### 8.2 Guardado desde `Status`

```java
Reading reading = statusMapper.toEntity(dto);
reading.setTime(Instant.now());
reading.setDevice(deviceDtoMapper.toEntity(deviceDto));

return readingRepository.save(reading);
```

En el flujo de estado directo, la lectura se asocia a un dispositivo ya existente y la marca temporal se fija en el momento de recepción.

## 9. Difusión en tiempo real por WebSocket

La configuración STOMP está en `StompWebSocketConfig.java`:

```java
registry.addEndpoint("/ws-iot").setAllowedOriginPatterns("*");
registry.enableSimpleBroker("/topic");
registry.setApplicationDestinationPrefixes("/app");
```

`TelemetryBroadcaster` publica dos tipos de eventos:

```java
String destination = "/topic/readings/" + readingDto.macAddress();
messagingTemplate.convertAndSend(destination, readingDto);
```

```java
String destination = "/topic/alerts/" + alertDto.username();
messagingTemplate.convertAndSend(destination, alertDto);
```

Por tanto:

- las lecturas se emiten por dispositivo;
- las alertas se emiten por usuario.

En el frontend, `WebsocketService.watchReadings(macAddress)` se suscribe a `/topic/readings/{macAddress}` y convierte cada mensaje en `ReadingResponse`.

## 10. Relación con alertas de potencia

Después de guardar cada lectura, el handler llama a:

```java
alertService.checkPowerThreshold(reading);
```

La regla compara la potencia activa medida con la potencia contratada:

```java
BigDecimal currentPowerKw = reading.getPowerW().divide(BigDecimal.valueOf(1000), RoundingMode.HALF_UP);
BigDecimal limitPowerKw = tariff.getContractedPowerKw();
```

Si se supera el límite, se crea una alerta `OVERPOWER`. Esta alerta queda persistida y además se envía por WebSocket, lo que permite que la aplicación reaccione sin esperar a una recarga manual.

## 11. Resumen técnico del flujo

```text
1. El Shelly publica en shellyplugsg3-9070694d3590/events/rpc o status/switch:0.
2. Mosquitto recibe el mensaje en el puerto 1883.
3. MqttPahoMessageDrivenChannelAdapter entrega el mensaje a Spring Integration.
4. mqttInboundFlow enruta por sufijo de topic.
5. El JSON se transforma a EventsRpc o Status.
6. DeviceMessageHandler guarda la lectura en ReadingService.
7. MapStruct convierte Wh a kWh y resuelve la MAC.
8. ReadingRepository persiste la lectura.
9. TelemetryBroadcaster publica ReadingResponse por /topic/readings/{macAddress}.
10. AlertService comprueba si la lectura supera la potencia contratada.
```

package com.joselumartos.jwtauthbackenddemo.services;

import com.joselumartos.jwtauthbackenddemo.dtos.DeviceDto;
import com.joselumartos.jwtauthbackenddemo.dtos.EventsRpc;
import com.joselumartos.jwtauthbackenddemo.dtos.Status;
import com.joselumartos.jwtauthbackenddemo.entities.Reading;
import com.joselumartos.jwtauthbackenddemo.mappers.ReadingResponseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeviceMessageHandler {

    private final DeviceService deviceService;
    private final ReadingService readingService;
    private final AlertService alertService;
    private final TelemetryBroadcaster broadcaster;
    private final ReadingResponseMapper readingResponseMapper;

    @ServiceActivator(inputChannel = "eventsRpcChannel")
    public void handleEventsRpc(Message<EventsRpc> mqttMessage) {
        EventsRpc payload = mqttMessage.getPayload();
        Reading reading = readingService.saveEntity(payload);
        broadcaster.broadcast(readingResponseMapper.toDto(reading));
        alertService.checkPowerThreshold(reading);
    }

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
}

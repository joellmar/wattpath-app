package com.joselumartos.jwtauthbackenddemo.services;

import com.joselumartos.jwtauthbackenddemo.dtos.AlertDto;
import com.joselumartos.jwtauthbackenddemo.dtos.ReadingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TelemetryBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcast(ReadingResponse readingDto) {
        String destination = "/topic/readings/" + readingDto.macAddress();
        messagingTemplate.convertAndSend(destination, readingDto);
    }

    public void broadcast(AlertDto alertDto) {
        String destination = "/topic/alerts/" + alertDto.username();
        messagingTemplate.convertAndSend(destination, alertDto);
    }
}

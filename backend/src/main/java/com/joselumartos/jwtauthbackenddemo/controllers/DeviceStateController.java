//package com.joselumartos.jwtauthbackenddemo.controllers;
//
//import lombok.RequiredArgsConstructor;
//import org.springframework.web.bind.annotation.*;
//
//import java.time.Duration;
//import java.time.Instant;
//
//@RestController
//@RequestMapping("/api/deviceMacAddress-state")
//@RequiredArgsConstructor
//public class DeviceStateController {
//
//    private final DeviceStateService deviceStateService;
//
//    @GetMapping("/{deviceId}")
//    public DeviceStateDto getLatestState(@PathVariable String deviceId) {
//        DeviceState state = deviceStateService.loadLatestState(deviceId);
//        return DeviceStateDto.from(state);
//    }
//
//    @GetMapping("/{deviceId}/history")
//    public List<DeviceStateDto> getRecentHistory(@PathVariable String deviceId, @RequestParam(defaultValue = "60") int minutes) {
//        Instant cutoff = Instant.now().minus(Duration.ofMinutes(minutes));
//        List<DeviceState> states = deviceStateService.loadStatesSince(deviceId, cutoff);
//
//        return states.stream()
//                .map(DeviceStateDto::from)
//                .toList();
//    }
//}

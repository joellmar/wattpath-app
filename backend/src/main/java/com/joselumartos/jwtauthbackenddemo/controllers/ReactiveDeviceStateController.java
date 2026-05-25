//package com.joselumartos.jwtauthbackenddemo.controllers;
//
//import lombok.RequiredArgsConstructor;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.PathVariable;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//import reactor.core.publisher.Mono;
//
//@RestController
//@RequestMapping("/api/reactive-deviceMacAddress-state")
//@RequiredArgsConstructor
//public class ReactiveDeviceStateController {
//
//    private final ReactiveDeviceStateService reactiveService;
//
//    @GetMapping("/{deviceId}")
//    public Mono<DeviceStateDto> getLatestStateReactive(@PathVariable String deviceId) {
//        return reactiveService.loadLatest(deviceId).map(DeviceStateDto::from);
//    }
//}

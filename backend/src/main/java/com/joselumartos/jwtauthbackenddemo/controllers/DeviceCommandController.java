//package com.joselumartos.jwtauthbackenddemo.controllers;
//
//import lombok.RequiredArgsConstructor;
//import org.springframework.messaging.handler.annotation.MessageMapping;
//import org.springframework.stereotype.Controller;
//
//@Controller
//@RequiredArgsConstructor
//public class DeviceCommandController {
//
//    private final DeviceCommandService commandService;
//
//    @MessageMapping("/deviceMacAddress-command")
//    public void handleCommand(DeviceCommandDto command) {
//        commandService.sendCommandToDevice(command);
//    }
//}

//package com.joselumartos.jwtauthbackenddemo.messaging;
//
//import com.hivemq.client.mqtt.MqttClient;
//import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
//
//
//public class HiveMqttExample {
//
//    private final Mqtt5AsyncClient client;
//
//    public HiveMqttExample() {
//        this.client = MqttClient.builder()
//                .useMqttVersion5()
//                .identifier("spring-gateway-hivemq")
//                .serverHost("localhost")
//                .serverPort(1883)
//                .buildAsync();
//    }
//
//    public void connectAndPublish() {
//        client.connectWith()
//                .simpleAuth()
//                .username("gateway-service")
//                .password("hivemq-secret".getBytes())
//                .applySimpleAuth()
//                .send()
//                .join();
//
//        client.publishWith()
//                .topic("devices/eau-claire-sensor-01/state")
//                .payload("{\"temperature\": 21.5}".getBytes())
//                .userProperties()
//                    .add("firmware", "1.0.3")
//                    .add("site", "Eau Claire")
//                    .applyUserProperties()
//                .send()
//                .join();
//    }
//}

//package com.joselumartos.jwtauthbackenddemo.messaging;
//
//import org.eclipse.paho.client.mqttv3.*;
//import org.springframework.stereotype.Component;
//
//@Component
//public class BasicMqttClient implements MqttCallback {
//    private final MqttClient client;
//
//    public BasicMqttClient() throws MqttException {
//        this.client = new MqttClient(
//                "tcp://localhost:1883",
//                "basic-gateway-client"
//        );
//
//        MqttConnectOptions options = new MqttConnectOptions();
//        options.setUserName("gateway-service");
//        options.setPassword("s3cr3t".toCharArray());
//
//        client.setCallback(this);
//        client.connect(options);
//        client.subscribe("devices/+/state", 1);
//    }
//    @Override
//    public void connectionLost(Throwable cause) {
//        // Lógica de reconexión
//    }
//
//    @Override
//    public void messageArrived(String topic, MqttMessage message) throws Exception {
//        byte[] payload = message.getPayload();
//        // pasar los bytes a un componente normalizador (ni idea de qué significa)
//    }
//
//    @Override
//    public void deliveryComplete(IMqttDeliveryToken token) {
//        // solo se usa para casos de uso inbound (ni idea)
//    }
//}

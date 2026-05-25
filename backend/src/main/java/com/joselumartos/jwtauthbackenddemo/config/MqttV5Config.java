//package com.joselumartos.jwtauthbackenddemo.config;
//
//import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.integration.channel.DirectChannel;
//import org.springframework.integration.config.EnableIntegration;
//import org.springframework.integration.core.MessageProducer;
//import org.springframework.integration.mqtt.inbound.Mqttv5PahoMessageDrivenChannelAdapter;
//import org.springframework.messaging.MessageChannel;
//
//@Configuration
//@EnableIntegration
//public class MqttV5Config {
//
//    @Bean
//    public MessageChannel mqttV5InputChannel() {
//        return new DirectChannel();
//    }
//
//    @Bean
//    public MessageProducer mqttV5Inbound() {
//        MqttConnectionOptions options = new MqttConnectionOptions();
//        options.setServerURIs(new String[] {"tcp:://localhost:1883"});
//        options.setUserName("gateway-service-v5");
//        options.setPassword("v5-secret".getBytes());
//
//        Mqttv5PahoMessageDrivenChannelAdapter adapter =
//                new Mqttv5PahoMessageDrivenChannelAdapter(
//                        options,
//                        "spring-gateway-client-v5",
//                        "devices/+/events"
//                );
//
//        adapter.setQos(1);
//        adapter.setOutputChannel(mqttV5InputChannel());
//        return adapter;
//    }
//}

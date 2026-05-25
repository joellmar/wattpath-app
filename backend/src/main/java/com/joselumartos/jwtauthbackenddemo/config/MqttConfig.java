package com.joselumartos.jwtauthbackenddemo.config;

import com.joselumartos.jwtauthbackenddemo.dtos.EventsRpc;
import com.joselumartos.jwtauthbackenddemo.dtos.Status;
import com.joselumartos.jwtauthbackenddemo.services.DeviceMessageHandler;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.Transformers;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

@Configuration
@EnableIntegration
public class MqttConfig {

    @Value("${mqtt.url}")
    private String mqttUrl;

    @Value("${mqtt.username}")
    private String mqttUsername;

    @Value("${mqtt.password}")
    private String mqttPassword;

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();

        options.setServerURIs(new String[] { mqttUrl });
        options.setUserName(mqttUsername);
        options.setPassword(mqttPassword.toCharArray());
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);

        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean
    public MessageChannel eventsRpcChannel() {
        return new DirectChannel();
    }

    @Bean MessageChannel statusChannel() {
        return new DirectChannel();
    }

    @Bean
    public MqttPahoMessageDrivenChannelAdapter mqttInbound() {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(
                    "backend-spring-iot",
                    mqttClientFactory(),
                    "shellyplugsg3-9070694d3590/#"
                );

        adapter.setQos(1);
        return adapter;
    }

    @Bean
    public IntegrationFlow mqttInboundFlow(MqttPahoMessageDrivenChannelAdapter mqttInbound) {
        return IntegrationFlow.from(mqttInbound)
                // 1. Extraemos el topic de las cabeceras
                .route(Message.class,
                        message -> {
                            String topic = message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC, String.class);
                            if (topic != null && topic.endsWith("/events/rpc")) return "EVENTS";
                            if (topic != null && topic.endsWith("/status/switch:0")) return "STATUS";
                            return "IGNORE";
                        },
                        // 2. Definimos las rutas según el valor del topic
                        router -> router
                                .subFlowMapping("EVENTS", eventsBranch -> eventsBranch
                                        .transform(Transformers.fromJson(EventsRpc.class))
                                        .channel("eventsRpcChannel")
                                )
                                .subFlowMapping("STATUS", statusBranch -> statusBranch
                                        .transform(Transformers.fromJson(Status.class))
                                        .channel("statusChannel")
                                )
                                .defaultOutputChannel("nullChannel")
                )
                .get();
    }
}

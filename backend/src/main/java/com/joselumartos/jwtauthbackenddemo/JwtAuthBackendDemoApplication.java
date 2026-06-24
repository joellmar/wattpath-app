package com.joselumartos.jwtauthbackenddemo;

import com.joselumartos.jwtauthbackenddemo.entities.Role;
import com.joselumartos.jwtauthbackenddemo.entities.UserEntity;
import com.joselumartos.jwtauthbackenddemo.repositories.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.joselumartos.jwtauthbackenddemo.config.SimulationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
@EnableConfigurationProperties(SimulationProperties.class)
public class JwtAuthBackendDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(JwtAuthBackendDemoApplication.class, args);
    }
}

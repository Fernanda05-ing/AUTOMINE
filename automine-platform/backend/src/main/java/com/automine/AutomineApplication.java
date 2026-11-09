package com.automine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

// La autenticacion es propia (usuarios en MySQL + JWT): se desactiva el usuario en memoria de Spring.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling
// Algunos repositorios se declaran como interfaces anidadas en su entidad.
@EnableJpaRepositories(considerNestedRepositories = true)
public class AutomineApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutomineApplication.class, args);
    }
}

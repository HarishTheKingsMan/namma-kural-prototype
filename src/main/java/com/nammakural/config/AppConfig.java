package com.nammakural.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    // This tells Spring: "Here is the ObjectMapper you were looking for!"
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
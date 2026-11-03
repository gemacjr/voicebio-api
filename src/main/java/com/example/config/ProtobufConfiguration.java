package com.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.protobuf.ProtobufHttpMessageConverter;

/**
 * Lets Spring MVC controllers return generated protobuf messages.
 * <p>
 * The converter writes {@code application/x-protobuf} (binary) and, because protobuf-java-util is on the classpath,
 * {@code application/json} using protobuf's canonical JSON mapping. Spring Boot places converter beans ahead of the
 * defaults, so protobuf messages are never handed to Jackson.
 */
@Configuration
public class ProtobufConfiguration {

    @Bean
    public ProtobufHttpMessageConverter protobufHttpMessageConverter() {
        return new ProtobufHttpMessageConverter();
    }
}

package com.example.security.jwt;

import com.example.config.ApplicationProperties;
import com.example.config.SecurityConfiguration;
import com.example.config.SecurityJwtConfiguration;
import com.example.config.WebConfigurer;
import com.example.management.SecurityMetersService;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(
    classes = {
      WebConfigurer.class,
      SecurityConfiguration.class,
      SecurityJwtConfiguration.class,
      SecurityMetersService.class,
      JwtAuthenticationTestUtils.class,
    })
@EnableConfigurationProperties(ApplicationProperties.class)
public @interface AuthenticationIntegrationTest {}

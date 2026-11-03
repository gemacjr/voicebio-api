package com.example.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.util.List;
import org.springdoc.core.customizers.ServerBaseUrlCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI documentation. Only served when the {@code api-docs} profile is active (see {@code
 * application.yml}).
 */
@Configuration
public class SpringDocConfiguration {

  @Bean
  public OpenAPI apiInfo(ApplicationProperties applicationProperties) {
    ApplicationProperties.ApiDocs apiDocs = applicationProperties.getApiDocs();
    return new OpenAPI()
        .info(
            new Info()
                .title(apiDocs.getTitle())
                .description(apiDocs.getDescription())
                .version(apiDocs.getVersion()));
  }

  @Bean
  public GroupedOpenApi apiGroup() {
    return GroupedOpenApi.builder().group("api").pathsToMatch("/api/**").build();
  }

  @Bean
  public GroupedOpenApi managementGroup() {
    return GroupedOpenApi.builder().group("management").pathsToMatch("/management/**").build();
  }

  @Bean
  public ServerBaseUrlCustomizer serverBaseUrlRequestCustomizer() {
    return (serverBaseUrl, request) -> {
      List<String> forwardedPrefix = request.getHeaders().get("X-Forwarded-Prefix");
      if (forwardedPrefix != null && !forwardedPrefix.isEmpty()) {
        return forwardedPrefix.getFirst();
      }
      return serverBaseUrl;
    };
  }
}

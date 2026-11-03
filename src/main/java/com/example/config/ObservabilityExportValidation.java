package com.example.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.util.StringUtils;

/**
 * Fails startup when a metrics or log-shipping profile is active without its settings.
 *
 * <p>Without this check, a missing variable does not stop the application: Spring Boot's binder
 * keeps an unresolvable placeholder such as {@code ${DATADOG_API_KEY}} as literal text, the app
 * starts normally, and a minute later every metrics push is rejected ("failed to send metrics to
 * datadog"), easily unnoticed.
 */
@Configuration
public class ObservabilityExportValidation {

  /**
   * Profile name to the environment variables it needs (application-&lt;profile&gt;.yml,
   * logback-spring.xml).
   */
  static final Map<String, List<String>> REQUIRED = new LinkedHashMap<>();

  static {
    REQUIRED.put("datadog", List.of("DATADOG_API_KEY"));
    REQUIRED.put("splunk", List.of("SPLUNK_HEC_URL", "SPLUNK_HEC_TOKEN"));
    REQUIRED.put("fluentd", List.of("FLUENTD_HOST"));
    // "dynatrace" needs nothing: metrics go to the local OneAgent
  }

  public ObservabilityExportValidation(Environment environment) {
    List<String> problems = problems(environment);
    if (!problems.isEmpty()) {
      throw new IllegalStateException(
          "Observability export is misconfigured: " + String.join("; ", problems));
    }
  }

  static List<String> problems(Environment environment) {
    List<String> problems = new ArrayList<>();
    REQUIRED.forEach(
        (profile, variables) -> {
          if (environment.acceptsProfiles(Profiles.of(profile))) {
            List<String> missing =
                variables.stream()
                    .filter(v -> !StringUtils.hasText(environment.getProperty(v)))
                    .toList();
            if (!missing.isEmpty()) {
              problems.add("profile '" + profile + "' requires " + String.join(", ", missing));
            }
          }
        });
    return problems;
  }
}

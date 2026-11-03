package com.example.karate;

import static org.assertj.core.api.Assertions.assertThat;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Runs ALL Karate features (including {@code @live} observability checks) against an already
 * running instance. Skipped unless {@code karate.baseUrl} is given. Start the app with {@code
 * ./mvnw}, then in a second terminal:
 *
 * <pre>
 * ./mvnw test-compile failsafe:integration-test failsafe:verify -Dit.test=KarateLiveIT \
 *     -Dkarate.baseUrl=http://localhost:8081
 * </pre>
 *
 * Do not use {@code ./mvnw verify} against an app started from {@code target/*.jar}: the build
 * repackages that jar while the app is running from it, and the app fails with {@code
 * NoClassDefFoundError}. Without {@code -Dkarate.token}, a token is fetched from the dev-only
 * {@code /api/authenticate}.
 */
@EnabledIfSystemProperty(named = "karate.baseUrl", matches = ".+")
class KarateLiveIT {

  @Test
  void allFeaturesAgainstRunningApp() {
    Results results =
        Runner.path(
                "classpath:karate/mbta",
                "classpath:karate/transit",
                "classpath:karate/routes",
                "classpath:karate/observability")
            .reportDir("target/karate-reports/live")
            .outputCucumberJson(true)
            .parallel(1);

    assertThat(results.getFailCount()).as(results.getErrorMessages()).isZero();
    assertThat(results.getScenariosPassed()).isPositive();
  }
}

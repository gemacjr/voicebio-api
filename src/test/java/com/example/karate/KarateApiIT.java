package com.example.karate;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.VoicebioApiApp;
import com.example.config.AsyncSyncConfiguration;
import com.example.security.jwt.JwtAuthenticationTestUtils;
import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import com.intuit.karate.core.MockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Self-contained Karate run, part of {@code ./mvnw verify}: starts the application on a random port with
 * the MBTA V3 API and the GTFS-Realtime feed replaced by Karate mock servers, then runs every {@code @mbta},
 * {@code @transit} and {@code @board} feature against it.
 * No network access, so the result is the same on every machine and in CI.
 * <p>
 * Features tagged {@code @live} need Actuator endpoints of a dev instance and run with {@link KarateLiveIT}.
 */
@SpringBootTest(classes = { VoicebioApiApp.class, AsyncSyncConfiguration.class }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class KarateApiIT {

    private static final MockServer MBTA_V3 = MockServer.feature("classpath:karate/mocks/mbta-v3-mock.feature").http(0).build();
    private static final MockServer TRANSIT_FEED = MockServer.feature("classpath:karate/mocks/gtfs-realtime-mock.feature").http(0).build();

    @DynamicPropertySource
    static void pointTodoClientAtMock(DynamicPropertyRegistry registry) {
        registry.add("spring.http.serviceclient.mbta.base-url", () -> "http://localhost:" + MBTA_V3.getPort());
        registry.add("spring.http.serviceclient.transit.base-url", () -> "http://localhost:" + TRANSIT_FEED.getPort());
    }

    @LocalServerPort
    private int port;

    @Value("${application.security.jwt.base64-secret}")
    private String jwtSecret;

    @AfterAll
    static void stopMocks() {
        MBTA_V3.stop();
        TRANSIT_FEED.stop();
    }

    @Test
    void apiFeatures() {
        Results results = Runner.path("classpath:karate/mbta", "classpath:karate/transit", "classpath:karate/routes", "classpath:karate/observability")
            .tags("@mbta,@transit,@board", "~@live")
            .systemProperty("karate.baseUrl", "http://localhost:" + port)
            .systemProperty("karate.token", JwtAuthenticationTestUtils.createValidToken(jwtSecret))
            .reportDir("target/karate-reports/self-contained")
            .outputCucumberJson(true)
            .parallel(1);

        assertThat(results.getFailCount()).as(results.getErrorMessages()).isZero();
        assertThat(results.getScenariosPassed()).isPositive();
    }
}

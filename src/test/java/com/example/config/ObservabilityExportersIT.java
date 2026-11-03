package com.example.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.VoicebioApiApp;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.datadog.DatadogMeterRegistry;
import io.micrometer.dynatrace.DynatraceMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.util.Collection;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies which metric registries each observability profile switches on, using the real
 * {@code application-<profile>.yml} files. Credentials are fake and every endpoint points at a closed local port,
 * so nothing leaves the machine.
 */
class ObservabilityExportersIT {

    private static Collection<MeterRegistry> registries(ApplicationContext context) {
        return context.getBeansOfType(MeterRegistry.class).values();
    }

    @Nested
    @SpringBootTest(classes = { VoicebioApiApp.class, AsyncSyncConfiguration.class })
    class DefaultProfile {

        @Autowired
        private ApplicationContext context;

        @Test
        void onlyPrometheusIsActive() {
            assertThat(registries(context)).hasAtLeastOneElementOfType(PrometheusMeterRegistry.class);
            assertThat(registries(context)).noneMatch(r -> r instanceof DatadogMeterRegistry || r instanceof DynatraceMeterRegistry);
        }
    }

    @Nested
    @ActiveProfiles({ "test", "datadog" })
    @SpringBootTest(
        classes = { VoicebioApiApp.class, AsyncSyncConfiguration.class },
        properties = { "DATADOG_API_KEY=test-api-key", "DATADOG_SITE_URI=http://127.0.0.1:9" }
    )
    class DatadogProfile {

        @Autowired
        private ApplicationContext context;

        @Test
        void datadogRegistryIsActive() {
            assertThat(registries(context)).hasAtLeastOneElementOfType(DatadogMeterRegistry.class);
            assertThat(context.getEnvironment().getProperty("management.metrics.tags.service")).isEqualTo("voicebioApi");
        }
    }

    @Nested
    @ActiveProfiles({ "test", "dynatrace" })
    @SpringBootTest(
        classes = { VoicebioApiApp.class, AsyncSyncConfiguration.class },
        properties = "management.dynatrace.metrics.export.uri=http://127.0.0.1:9/metrics/ingest"
    )
    class DynatraceProfile {

        @Autowired
        private ApplicationContext context;

        @Test
        void dynatraceRegistryIsActiveWithoutAnyToken() {
            assertThat(registries(context)).hasAtLeastOneElementOfType(DynatraceMeterRegistry.class);
            assertThat(context.getEnvironment().getProperty("management.dynatrace.metrics.export.api-token")).isNull();
        }
    }
}

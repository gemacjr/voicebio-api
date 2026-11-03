package com.example.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ObservabilityExportValidationTest {

    @Test
    void noVendorProfileNeedsNothing() {
        assertThat(ObservabilityExportValidation.problems(new MockEnvironment())).isEmpty();
    }

    @Test
    void datadogWithoutApiKeyFailsStartup() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod", "datadog");

        assertThatThrownBy(() -> new ObservabilityExportValidation(env))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("profile 'datadog' requires DATADOG_API_KEY");
    }

    @Test
    void blankValueCountsAsMissing() {
        MockEnvironment env = new MockEnvironment().withProperty("DATADOG_API_KEY", "  ");
        env.setActiveProfiles("datadog");

        assertThat(ObservabilityExportValidation.problems(env)).containsExactly("profile 'datadog' requires DATADOG_API_KEY");
    }

    @Test
    void splunkReportsEveryMissingVariable() {
        MockEnvironment env = new MockEnvironment().withProperty("SPLUNK_HEC_URL", "https://splunk.example.com:8088");
        env.setActiveProfiles("splunk");

        assertThat(ObservabilityExportValidation.problems(env)).containsExactly("profile 'splunk' requires SPLUNK_HEC_TOKEN");
    }

    @Test
    void fluentdNeedsAHost() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("fluentd");

        assertThat(ObservabilityExportValidation.problems(env)).containsExactly("profile 'fluentd' requires FLUENTD_HOST");
    }

    @Test
    void dynatraceOneAgentModeNeedsNothing() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod", "dynatrace");

        assertThat(ObservabilityExportValidation.problems(env)).isEmpty();
    }

    @Test
    void configuredProfilesPass() {
        MockEnvironment env = new MockEnvironment()
            .withProperty("DATADOG_API_KEY", "key")
            .withProperty("SPLUNK_HEC_URL", "https://splunk.example.com:8088")
            .withProperty("SPLUNK_HEC_TOKEN", "token")
            .withProperty("FLUENTD_HOST", "doppler.example.com");
        env.setActiveProfiles("datadog", "splunk", "fluentd", "dynatrace");

        assertThatCode(() -> new ObservabilityExportValidation(env)).doesNotThrowAnyException();
    }
}

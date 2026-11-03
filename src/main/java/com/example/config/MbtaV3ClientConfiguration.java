package com.example.config;

import com.example.client.mbta.MbtaApiException;
import com.example.client.mbta.MbtaNotFoundException;
import com.example.client.mbta.MbtaRateLimitedException;
import com.example.client.mbta.MbtaV3Client;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

/**
 * Registers {@link MbtaV3Client} in the {@value MbtaV3Client#GROUP} HTTP interface group and adds what is specific to
 * MBTA: the optional API key, a gauge of the remaining rate limit, the timing log and status-to-exception mapping.
 */
@Configuration
@ImportHttpServices(group = MbtaV3Client.GROUP, types = MbtaV3Client.class)
public class MbtaV3ClientConfiguration {

    static final String API_KEY_HEADER = "x-api-key";
    static final String RATE_LIMIT_REMAINING = "x-ratelimit-remaining";
    static final String RATE_LIMIT_RESET = "x-ratelimit-reset";

    @Bean
    public RestClientHttpServiceGroupConfigurer mbtaClientGroupConfigurer(ApplicationProperties properties, MeterRegistry meterRegistry) {
        AtomicInteger remaining = new AtomicInteger(-1);
        Gauge.builder("mbta.ratelimit.remaining", remaining, AtomicInteger::get)
            .description("Requests left in the current MBTA rate-limit window (-1 until the first call)")
            .register(meterRegistry);
        return groups ->
            groups
                .filterByName(MbtaV3Client.GROUP)
                .forEachClient((group, builder) -> customize(builder, properties.getMbta().getApiKey(), remaining, Clock.systemUTC()));
    }

    /** Applies the MBTA specific behaviour to a client builder (also used by tests). */
    static RestClient.Builder customize(RestClient.Builder builder, String apiKey, AtomicInteger rateLimitRemaining, Clock clock) {
        if (StringUtils.hasText(apiKey)) {
            builder.defaultHeader(API_KEY_HEADER, apiKey); // only when configured: an empty key would be rejected
        }
        return builder
            .requestInterceptor(OutboundCallLogging.timingLog("MBTA V3"))
            .requestInterceptor(rateLimitTracker(rateLimitRemaining))
            .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                HttpStatusCode status = response.getStatusCode();
                String message = "MBTA API returned HTTP " + status.value() + " for " + request.getMethod() + " " + request.getURI().getPath();
                if (status.isSameCodeAs(HttpStatus.NOT_FOUND)) {
                    throw new MbtaNotFoundException(message);
                }
                if (status.isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
                    throw new MbtaRateLimitedException(retryAfterSeconds(response.getHeaders().getFirst(RATE_LIMIT_RESET), clock));
                }
                throw new MbtaApiException(message);
            });
    }

    private static ClientHttpRequestInterceptor rateLimitTracker(AtomicInteger remaining) {
        return (request, body, execution) -> {
            var response = execution.execute(request, body);
            String value = response.getHeaders().getFirst(RATE_LIMIT_REMAINING);
            if (value != null) {
                try {
                    remaining.set(Integer.parseInt(value.trim()));
                } catch (NumberFormatException ignored) {
                    // a malformed header must never fail the call
                }
            }
            return response;
        };
    }

    /** MBTA sends the epoch second at which the window resets; at least 1 s, 60 s when unknown. */
    static long retryAfterSeconds(String resetEpochSeconds, Clock clock) {
        try {
            return Math.max(1, Long.parseLong(resetEpochSeconds.trim()) - clock.instant().getEpochSecond());
        } catch (RuntimeException e) {
            return 60;
        }
    }
}

package com.example.config;

import com.example.client.transit.GtfsRealtimeClient;
import com.example.client.transit.TransitFeedException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.converter.protobuf.ProtobufHttpMessageConverter;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

/**
 * Registers {@link GtfsRealtimeClient} in the {@value GtfsRealtimeClient#GROUP} HTTP interface group.
 * <p>
 * Spring's RestClient has no protobuf converter by default, so one is added here; it decodes
 * {@code application/x-protobuf} bodies into generated protobuf message classes.
 */
@Configuration
@ImportHttpServices(group = GtfsRealtimeClient.GROUP, types = GtfsRealtimeClient.class)
public class TransitClientConfiguration {

    @Bean
    public RestClientHttpServiceGroupConfigurer transitClientGroupConfigurer() {
        return groups -> groups.filterByName(GtfsRealtimeClient.GROUP).forEachClient((group, builder) -> customize(builder));
    }

    /** Applies the transit-feed specific behaviour to a client builder (also used by tests). */
    static RestClient.Builder customize(RestClient.Builder builder) {
        return builder
            .messageConverters(converters -> converters.addFirst(new ProtobufHttpMessageConverter()))
            .requestInterceptor(OutboundCallLogging.timingLog("Transit feed"))
            .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                throw new TransitFeedException(
                    "Transit feed returned HTTP " + response.getStatusCode().value() + " for " + request.getURI().getPath()
                );
            });
    }
}

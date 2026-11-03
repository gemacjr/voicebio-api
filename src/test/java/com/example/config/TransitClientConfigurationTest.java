package com.example.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.client.transit.GtfsRealtimeClient;
import com.example.client.transit.TransitFeedException;
import com.example.transit.GtfsFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/** The generated {@link GtfsRealtimeClient} decoding binary protobuf, with the feed replaced by a mock server. */
class TransitClientConfigurationTest {

    private static final String BASE_URL = "https://feed.test";

    private MockRestServiceServer server;
    private GtfsRealtimeClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = TransitClientConfiguration.customize(RestClient.builder().baseUrl(BASE_URL));
        server = MockRestServiceServer.bindTo(builder).build();
        client = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(builder.build())).build().createClient(GtfsRealtimeClient.class);
    }

    @Test
    void decodesBinaryProtobufIntoTheGeneratedClass() {
        server
            .expect(requestTo(BASE_URL + "/realtime/VehiclePositions.pb"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Accept", MediaType.APPLICATION_PROTOBUF_VALUE))
            .andRespond(withSuccess(GtfsFixtures.vehiclePositionsBytes(), MediaType.APPLICATION_PROTOBUF));

        assertThat(client.vehiclePositions()).isEqualTo(GtfsFixtures.vehiclePositions());
        server.verify();
    }

    @Test
    void upstreamErrorBecomesTransitFeedException() {
        server.expect(requestTo(BASE_URL + "/realtime/VehiclePositions.pb")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.vehiclePositions())
            .isInstanceOf(TransitFeedException.class)
            .hasMessage("Transit feed returned HTTP 503 for /realtime/VehiclePositions.pb");
    }
}

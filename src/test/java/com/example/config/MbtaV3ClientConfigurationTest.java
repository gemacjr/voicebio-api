package com.example.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.client.mbta.JsonApiResource;
import com.example.client.mbta.MbtaApiException;
import com.example.client.mbta.MbtaNotFoundException;
import com.example.client.mbta.MbtaRateLimitedException;
import com.example.client.mbta.MbtaV3Client;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * The generated {@link MbtaV3Client}: JSON:API requests and decoding, API key, rate limit and error
 * mapping.
 */
class MbtaV3ClientConfigurationTest {

  private static final String BASE_URL = "https://mbta.test";
  private static final MediaType JSON_API = MediaType.parseMediaType(MbtaV3Client.JSON_API);
  private static final Instant NOW = Instant.parse("2026-11-03T21:00:00Z");

  private MockRestServiceServer server;
  private MbtaV3Client client;
  private final AtomicInteger rateLimitRemaining = new AtomicInteger(-1);

  static String fixture(String name) throws IOException {
    return new ClassPathResource("fixtures/mbta/" + name)
        .getContentAsString(StandardCharsets.UTF_8);
  }

  private void createClient(String apiKey) {
    RestClient.Builder builder =
        MbtaV3ClientConfiguration.customize(
            RestClient.builder().baseUrl(BASE_URL),
            apiKey,
            rateLimitRemaining,
            Clock.fixed(NOW, ZoneOffset.UTC));
    server = MockRestServiceServer.bindTo(builder).build();
    client =
        HttpServiceProxyFactory.builderFor(RestClientAdapter.create(builder.build()))
            .build()
            .createClient(MbtaV3Client.class);
  }

  @Test
  void sendsJsonApiFilterParametersAndDecodesTheDocument() throws IOException {
    createClient(null);
    server
        .expect(requestTo(BASE_URL + "/routes?filter%5Btype%5D=0%2C1"))
        .andExpect(header(HttpHeaders.ACCEPT, MbtaV3Client.JSON_API))
        .andRespond(withSuccess(fixture("routes-subway.json"), JSON_API));

    var routes = client.routes("0,1").data();

    assertThat(routes).extracting(JsonApiResource::id).containsExactly("Red", "Green-B");
    assertThat(routes.getFirst().attributes().directionDestinations())
        .containsExactly("Ashmont/Braintree", "Alewife");
    server.verify();
  }

  @Test
  void readsToOneAndToManyRelationshipsTheSameWay() throws IOException {
    createClient(null);
    server
        .expect(
            requestTo(
                BASE_URL
                    + "/stops?include=child_stops&fields%5Bstop%5D=name,platform_name,location_type,latitude,longitude&filter%5Broute%5D=Red"))
        .andRespond(withSuccess(fixture("stations-red.json"), JSON_API));

    var document = client.stationsWithPlatforms("Red");

    assertThat(document.data().getFirst().relationships().get("child_stops").ids())
        .containsExactly("70085", "70089", "door-jfk-1"); // array
    assertThat(document.included().getFirst().relatedId("parent_station"))
        .contains("place-jfk"); // single object
  }

  @Test
  void nullRelationshipDataIsEmpty() throws IOException {
    createClient(null);
    server
        .expect(
            requestTo(
                BASE_URL
                    + "/predictions?sort=arrival_time&filter%5Bstop%5D=place-pktrm&filter%5Broute%5D=Red&page%5Blimit%5D=5"))
        .andRespond(withSuccess(fixture("predictions-pktrm.json"), JSON_API));

    var predictions = client.predictions("place-pktrm", "Red", 5).data();

    assertThat(predictions.get(0).relatedId("vehicle")).contains("R-548C8C9A");
    assertThat(predictions.get(1).relatedId("vehicle")).isEmpty(); // "vehicle": {"data": null}
  }

  @Test
  void apiKeyIsSentOnlyWhenConfigured() throws IOException {
    createClient("secret-key");
    server
        .expect(requestTo(BASE_URL + "/routes/Red"))
        .andExpect(header("x-api-key", "secret-key"))
        .andRespond(withSuccess(fixture("route-red.json"), JSON_API));
    client.route("Red");
    server.verify();

    createClient("  ");
    server
        .expect(requestTo(BASE_URL + "/routes/Red"))
        .andExpect(headerDoesNotExist("x-api-key"))
        .andRespond(withSuccess(fixture("route-red.json"), JSON_API));
    client.route("Red");
    server.verify();
  }

  @Test
  void tracksTheRemainingRateLimit() throws IOException {
    createClient(null);
    server
        .expect(requestTo(BASE_URL + "/routes/Red"))
        .andRespond(
            withSuccess(fixture("route-red.json"), JSON_API).header("x-ratelimit-remaining", "17"));

    client.route("Red");

    assertThat(rateLimitRemaining.get()).isEqualTo(17);
  }

  @Test
  void notFoundBecomesMbtaNotFoundException() throws IOException {
    createClient(null);
    server
        .expect(requestTo(BASE_URL + "/routes/Nope"))
        .andRespond(
            withStatus(HttpStatus.NOT_FOUND).contentType(JSON_API).body(fixture("error-404.json")));

    assertThatThrownBy(() -> client.route("Nope"))
        .isInstanceOf(MbtaNotFoundException.class)
        .hasMessage("MBTA API returned HTTP 404 for GET /routes/Nope");
  }

  @Test
  void rateLimitedBecomesRetryableWithTheResetTime() {
    createClient(null);
    server
        .expect(requestTo(BASE_URL + "/routes/Red"))
        .andRespond(
            withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .header("x-ratelimit-reset", String.valueOf(NOW.getEpochSecond() + 42)));

    assertThatThrownBy(() -> client.route("Red"))
        .isInstanceOfSatisfying(
            MbtaRateLimitedException.class, e -> assertThat(e.retryAfterSeconds()).isEqualTo(42));
  }

  @Test
  void serverErrorBecomesMbtaApiException() {
    createClient(null);
    server.expect(requestTo(BASE_URL + "/routes/Red")).andRespond(withServerError());

    assertThatThrownBy(() -> client.route("Red"))
        .isExactlyInstanceOf(MbtaApiException.class)
        .hasMessageContaining("500");
  }

  @Test
  void retryAfterFallsBackSafely() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    assertThat(MbtaV3ClientConfiguration.retryAfterSeconds(null, clock)).isEqualTo(60);
    assertThat(MbtaV3ClientConfiguration.retryAfterSeconds("not-a-number", clock)).isEqualTo(60);
    assertThat(
            MbtaV3ClientConfiguration.retryAfterSeconds(
                String.valueOf(NOW.getEpochSecond() - 5), clock))
        .isEqualTo(1);
  }
}

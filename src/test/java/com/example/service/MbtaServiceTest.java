package com.example.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.client.mbta.JsonApiDocument;
import com.example.client.mbta.JsonApiSingle;
import com.example.client.mbta.MbtaAttributes;
import com.example.client.mbta.MbtaNotFoundException;
import com.example.client.mbta.MbtaV3Client;
import com.example.service.dto.MbtaDtos.DirectionDTO;
import com.example.service.dto.MbtaDtos.PlatformDTO;
import com.example.service.dto.MbtaDtos.StationDTO;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Mapping from MBTA's JSON:API documents (real-shaped fixtures) to this API's DTOs. */
class MbtaServiceTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final MbtaV3Client client = mock(MbtaV3Client.class);
  private final MbtaService service = new MbtaService(client);

  static <T> T read(String fixture, TypeReference<T> type) throws IOException {
    return JSON.readValue(
        new ClassPathResource("fixtures/mbta/" + fixture)
            .getContentAsString(StandardCharsets.UTF_8),
        type);
  }

  @Test
  void routeIsFlattenedWithNamedDirections() throws IOException {
    when(client.route("Red"))
        .thenReturn(
            read("route-red.json", new TypeReference<JsonApiSingle<MbtaAttributes.Route>>() {}));

    var route = service.route("Red");

    assertThat(route.name()).isEqualTo("Red Line");
    assertThat(route.kind()).isEqualTo("SUBWAY");
    assertThat(route.directions())
        .containsExactly(
            new DirectionDTO(0, "South", "Ashmont/Braintree"),
            new DirectionDTO(1, "North", "Alewife"));
  }

  @Test
  void lightRailUsesTheLongName() throws IOException {
    when(client.routes("0,1"))
        .thenReturn(
            read(
                "routes-subway.json",
                new TypeReference<JsonApiDocument<MbtaAttributes.Route>>() {}));

    assertThat(service.routes("0,1"))
        .extracting(r -> r.id() + "=" + r.name() + "/" + r.kind())
        .containsExactly("Red=Red Line/SUBWAY", "Green-B=Green Line B/LIGHT_RAIL");
  }

  @Test
  void unknownRouteIsADomainNotFound() {
    when(client.route("Nope")).thenThrow(new MbtaNotFoundException("404"));

    assertThatThrownBy(() -> service.route("Nope"))
        .isInstanceOf(RouteNotFoundException.class)
        .hasMessage("Route Nope does not exist");
  }

  @Test
  void stationsKeepOnlyTheirBoardingPlatforms() throws IOException {
    when(client.stationsWithPlatforms("Red"))
        .thenReturn(
            read(
                "stations-red.json", new TypeReference<JsonApiDocument<MbtaAttributes.Stop>>() {}));

    var stations = service.stations("Red");

    assertThat(stations).extracting(StationDTO::name).containsExactly("JFK/UMass", "Park Street");
    assertThat(stations.getFirst().platforms())
        .containsExactly(
            new PlatformDTO("70085", "Ashmont/Braintree"),
            new PlatformDTO("70089", "Alewife")); // the entrance is not a platform
  }

  @Test
  void predictionsReadIdsFromRelationships() throws IOException {
    when(client.predictions("place-pktrm", "Red", 5))
        .thenReturn(
            read(
                "predictions-pktrm.json",
                new TypeReference<JsonApiDocument<MbtaAttributes.Prediction>>() {}));

    var predictions = service.predictions("place-pktrm", "Red", 5);

    assertThat(predictions.getFirst().vehicleId()).isEqualTo("R-548C8C9A");
    assertThat(predictions.getFirst().stopId()).isEqualTo("70076");
    assertThat(predictions.getFirst().arrivalTime())
        .isEqualTo(OffsetDateTime.parse("2026-11-03T16:13:14-05:00"));
    assertThat(predictions.get(1).vehicleId()).isNull();
    assertThat(predictions.get(1).status()).isEqualTo("Approaching");
  }

  @Test
  void alertsExposeTheirActivePeriod() throws IOException {
    when(client.alerts("Red"))
        .thenReturn(
            read("alerts-red.json", new TypeReference<JsonApiDocument<MbtaAttributes.Alert>>() {}));

    var alert = service.alerts("Red").getFirst();

    assertThat(alert.effect()).isEqualTo("NOTICE");
    assertThat(alert.activeFrom()).isEqualTo(OffsetDateTime.parse("2026-10-07T15:03:54-04:00"));
    assertThat(alert.activeUntil()).isNull();
  }
}

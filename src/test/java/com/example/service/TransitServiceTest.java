package com.example.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.api.transit.v1.Vehicle;
import com.example.api.transit.v1.VehicleList;
import com.example.api.transit.v1.VehicleStatus;
import com.example.transit.GtfsFixtures;
import com.google.protobuf.Timestamp;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Mapping from the upstream GTFS-Realtime model to this API's transit.proto contract. */
class TransitServiceTest {

  private static final Instant NOW = Instant.ofEpochSecond(GtfsFixtures.FEED_TIMESTAMP + 7);

  private final CachedVehicleFeed feed = mock(CachedVehicleFeed.class);
  private SimpleMeterRegistry meterRegistry;
  private TransitService service;

  @BeforeEach
  void setUp() {
    when(feed.vehiclePositions()).thenReturn(GtfsFixtures.vehiclePositions());
    meterRegistry = new SimpleMeterRegistry();
    service = new TransitService(feed, meterRegistry, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void servesOnlyLiveVehiclesSortedById() {
    VehicleList list = service.vehicles(null, 100);

    assertThat(list.getVehiclesList())
        .extracting(Vehicle::getVehicleId)
        .containsExactly("R-1", "R-2", "Y-39");
    assertThat(list.getTotalInFeed())
        .isEqualTo(3); // deleted entity and alert are not vehicles to serve
    assertThat(list.getFeedTimestamp())
        .isEqualTo(Timestamp.newBuilder().setSeconds(GtfsFixtures.FEED_TIMESTAMP).build());
  }

  @Test
  void filtersByRouteAndKeepsTheUnfilteredTotal() {
    VehicleList list = service.vehicles("Red", 100);

    assertThat(list.getVehiclesList())
        .extracting(Vehicle::getRouteId)
        .containsOnly("Red")
        .hasSize(2);
    assertThat(list.getTotalInFeed()).isEqualTo(3);
  }

  @Test
  void blankRouteMeansAllRoutes() {
    assertThat(service.vehicles("  ", 100).getVehiclesCount()).isEqualTo(3);
  }

  @Test
  void appliesTheLimitAfterSorting() {
    assertThat(service.vehicles(null, 2).getVehiclesList())
        .extracting(Vehicle::getVehicleId)
        .containsExactly("R-1", "R-2");
  }

  @Test
  void mapsEveryFieldTheAgencySent() {
    Vehicle expected =
        Vehicle.newBuilder()
            .setVehicleId("R-1")
            .setLabel("1827")
            .setRouteId("Red")
            .setTripId("77871166")
            .setLatitude(42.30377)
            .setLongitude(-71.05578)
            .setBearing(195f)
            .setSpeedMetersPerSecond(8.5f)
            .setStatus(VehicleStatus.INCOMING_AT)
            .setStopId("70089")
            .setObservedAt(Timestamp.newBuilder().setSeconds(GtfsFixtures.FEED_TIMESTAMP - 20))
            .setDirectionId(1)
            .build();

    assertThat(service.vehicle("R-1")).isEqualTo(expected);
  }

  @Test
  void missingOptionalFieldsStayUnset() {
    Vehicle vehicle = service.vehicle("R-2");

    assertThat(vehicle.getLabel()).isEmpty();
    assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.VEHICLE_STATUS_UNSPECIFIED);
    assertThat(vehicle.hasObservedAt()).isFalse();
    assertThat(vehicle.getBearing()).isZero();
    assertThat(vehicle.hasDirectionId()).isFalse(); // proto3 "optional": absent, not 0
  }

  @Test
  void widensFloatsWithoutBinaryNoise() {
    // (double) 42.30377f would be 42.30376815795898
    assertThat(service.vehicle("R-1").getLatitude()).isEqualTo(42.30377);
    // a float carries ~7 significant digits (about 1 m here): the shortest decimal for 42.212929f
    // is 42.21293
    assertThat(TransitService.widen(42.212929f)).isEqualTo(42.21293);
  }

  @Test
  void unknownVehicleIsNotFound() {
    assertThatThrownBy(() -> service.vehicle("nope"))
        .isInstanceOf(VehicleNotFoundException.class)
        .hasMessageContaining("nope");
  }

  @Test
  void deletedVehicleIsNotFound() {
    assertThatThrownBy(() -> service.vehicle("R-DELETED"))
        .isInstanceOf(VehicleNotFoundException.class);
  }

  @Test
  void feedAgeGaugeReportsSecondsSinceTheFeedWasGenerated() {
    assertThat(meterRegistry.get("transit.feed.age").gauge().value()).isNaN(); // nothing served yet

    service.vehicles(null, 1);

    assertThat(meterRegistry.get("transit.feed.age").gauge().value()).isEqualTo(7.0);
  }
}

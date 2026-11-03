package com.example.service;

import com.example.api.transit.v1.Vehicle;
import com.example.api.transit.v1.VehicleList;
import com.example.api.transit.v1.VehicleStatus;
import com.google.protobuf.Timestamp;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.annotation.Observed;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Vehicle positions from a GTFS-Realtime feed, mapped to this API's own protobuf contract ({@code
 * transit.proto}).
 *
 * <p>The upstream model ({@code gtfs-realtime.proto}, proto2, optional fields everywhere) never
 * leaves this class. Observability: {@code @Observed} timer {@code transit.service}, and gauge
 * {@code transit.feed.age} (seconds since the agency generated the feed we last served; a growing
 * value means the upstream feed is stale).
 */
@Slf4j
@Service
@Observed(name = "transit.service", contextualName = "transit-service")
public class TransitService {

  private final CachedVehicleFeed feed;
  private final Clock clock;
  private final AtomicLong lastFeedTimestamp = new AtomicLong();

  @Autowired // two constructors: tell Spring which one to use (the other lets tests fix the clock)
  public TransitService(CachedVehicleFeed feed, MeterRegistry meterRegistry) {
    this(feed, meterRegistry, Clock.systemUTC());
  }

  TransitService(CachedVehicleFeed feed, MeterRegistry meterRegistry, Clock clock) {
    this.feed = feed;
    this.clock = clock;
    Gauge.builder("transit.feed.age", this, TransitService::feedAgeSeconds)
        .description("Seconds since the agency generated the last feed served")
        .baseUnit("seconds")
        .register(meterRegistry);
  }

  /**
   * @param routeId only vehicles on this route (exact GTFS route id, e.g. "Red"); all routes when
   *     null or blank
   * @param limit maximum number of vehicles returned, sorted by vehicle id
   */
  public VehicleList vehicles(String routeId, int limit) {
    FeedMessage current = currentFeed();
    List<Vehicle> vehicles =
        vehiclePositions(current)
            .filter(
                v ->
                    routeId == null
                        || routeId.isBlank()
                        || routeId.equals(v.getTrip().getRouteId()))
            .map(TransitService::toVehicle)
            .sorted(Comparator.comparing(Vehicle::getVehicleId))
            .limit(limit)
            .toList();
    log.info(
        "Serving {} vehicles{}",
        vehicles.size(),
        routeId == null || routeId.isBlank() ? "" : " on route " + routeId);
    return VehicleList.newBuilder()
        .setFeedTimestamp(timestamp(current.getHeader().getTimestamp()))
        .setTotalInFeed((int) vehiclePositions(current).count())
        .addAllVehicles(vehicles)
        .build();
  }

  public Vehicle vehicle(String vehicleId) {
    return vehiclePositions(currentFeed())
        .filter(v -> vehicleId.equals(v.getVehicle().getId()))
        .findFirst()
        .map(TransitService::toVehicle)
        .orElseThrow(() -> new VehicleNotFoundException(vehicleId));
  }

  private FeedMessage currentFeed() {
    FeedMessage current = feed.vehiclePositions();
    lastFeedTimestamp.set(current.getHeader().getTimestamp());
    return current;
  }

  private static Stream<VehiclePosition> vehiclePositions(FeedMessage feed) {
    return feed.getEntityList().stream()
        .filter(e -> !e.getIsDeleted() && e.hasVehicle())
        .map(FeedEntity::getVehicle);
  }

  double feedAgeSeconds() {
    long generated = lastFeedTimestamp.get();
    return generated == 0 ? Double.NaN : clock.instant().getEpochSecond() - generated;
  }

  /** GTFS-Realtime fields are proto2 "optional": copy only what the agency actually sent. */
  static Vehicle toVehicle(VehiclePosition position) {
    Vehicle.Builder vehicle = Vehicle.newBuilder();
    if (position.getVehicle().hasId()) vehicle.setVehicleId(position.getVehicle().getId());
    if (position.getVehicle().hasLabel()) vehicle.setLabel(position.getVehicle().getLabel());
    if (position.getTrip().hasRouteId()) vehicle.setRouteId(position.getTrip().getRouteId());
    if (position.getTrip().hasTripId()) vehicle.setTripId(position.getTrip().getTripId());
    if (position.getTrip().hasDirectionId())
      vehicle.setDirectionId(position.getTrip().getDirectionId());
    if (position.hasPosition()) {
      // GTFS sends float; widening float to double directly prints noise (42.212928771972656), so
      // go via its decimal form
      vehicle
          .setLatitude(widen(position.getPosition().getLatitude()))
          .setLongitude(widen(position.getPosition().getLongitude()));
      if (position.getPosition().hasBearing())
        vehicle.setBearing(position.getPosition().getBearing());
      if (position.getPosition().hasSpeed())
        vehicle.setSpeedMetersPerSecond(position.getPosition().getSpeed());
    }
    if (position.hasCurrentStatus()) vehicle.setStatus(toStatus(position.getCurrentStatus()));
    if (position.hasStopId()) vehicle.setStopId(position.getStopId());
    if (position.hasTimestamp()) vehicle.setObservedAt(timestamp(position.getTimestamp()));
    return vehicle.build();
  }

  private static VehicleStatus toStatus(VehiclePosition.VehicleStopStatus status) {
    return switch (status) {
      case INCOMING_AT -> VehicleStatus.INCOMING_AT;
      case STOPPED_AT -> VehicleStatus.STOPPED_AT;
      case IN_TRANSIT_TO -> VehicleStatus.IN_TRANSIT_TO;
    };
  }

  static double widen(float value) {
    return Double.parseDouble(Float.toString(value));
  }

  private static Timestamp timestamp(long epochSeconds) {
    return Timestamp.newBuilder().setSeconds(epochSeconds).build();
  }
}

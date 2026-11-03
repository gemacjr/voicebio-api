package com.example.client.mbta;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * The {@code attributes} of the MBTA V3 resources this service reads
 * (https://api-v3.mbta.com/docs/swagger). Only the fields we use are declared; MBTA's other fields
 * are ignored when reading.
 */
public final class MbtaAttributes {

  private MbtaAttributes() {}

  public record Route(
      @JsonProperty("long_name") String longName,
      @JsonProperty("short_name") String shortName,
      String color,
      @JsonProperty("text_color") String textColor,
      Integer type,
      String description,
      @JsonProperty("direction_names") List<String> directionNames,
      @JsonProperty("direction_destinations") List<String> directionDestinations) {}

  /**
   * {@code location_type}: 0 = platform / stop, 1 = parent station, 2 = entrance, 3 = generic node.
   */
  public record Stop(
      String name,
      @JsonProperty("platform_name") String platformName,
      @JsonProperty("location_type") Integer locationType,
      Double latitude,
      Double longitude) {}

  public record Prediction(
      @JsonProperty("arrival_time") OffsetDateTime arrivalTime,
      @JsonProperty("departure_time") OffsetDateTime departureTime,
      @JsonProperty("direction_id") Integer directionId,
      String status,
      @JsonProperty("stop_sequence") Integer stopSequence) {}

  public record Alert(
      String header,
      String effect,
      Integer severity,
      String lifecycle,
      @JsonProperty("active_period") List<ActivePeriod> activePeriod) {
    public record ActivePeriod(OffsetDateTime start, OffsetDateTime end) {}
  }
}

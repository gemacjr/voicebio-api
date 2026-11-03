package com.example.client.transit;

import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import org.springframework.http.MediaType;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * Declarative client for a GTFS-Realtime feed (MBTA, Boston, by default): binary Protocol Buffers over HTTP.
 * <p>
 * The response body is decoded into the {@link FeedMessage} class generated from {@code gtfs-realtime.proto} by the
 * protobuf converter registered in {@code TransitClientConfiguration}. Base URL and timeouts come from
 * {@code spring.http.serviceclient.transit.*}. Upstream errors throw {@link TransitFeedException}.
 */
@HttpExchange(accept = MediaType.APPLICATION_PROTOBUF_VALUE)
public interface GtfsRealtimeClient {
    String GROUP = "transit";

    /** All vehicles currently reported by the agency (a full dataset, typically refreshed every 10 to 15 seconds). */
    @GetExchange("/realtime/VehiclePositions.pb")
    FeedMessage vehiclePositions();
}

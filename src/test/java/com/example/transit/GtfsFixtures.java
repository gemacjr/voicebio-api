package com.example.transit;

import com.google.transit.realtime.GtfsRealtime.Alert;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedHeader;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.Position;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor;
import com.google.transit.realtime.GtfsRealtime.VehicleDescriptor;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition.VehicleStopStatus;

/**
 * A small GTFS-Realtime vehicle feed shaped like the real MBTA one, including its awkward parts:
 * optional fields that are missing, a deleted entity, and an entity that is not a vehicle.
 * Used by unit tests, integration tests and the Karate mock server ({@code Java.type(...)}).
 */
public final class GtfsFixtures {

    /** 2026-11-03T20:49:59Z */
    public static final long FEED_TIMESTAMP = 1_793_738_999L;

    private GtfsFixtures() {}

    public static FeedMessage vehiclePositions() {
        return FeedMessage.newBuilder()
            .setHeader(
                FeedHeader.newBuilder()
                    .setGtfsRealtimeVersion("2.0")
                    .setIncrementality(FeedHeader.Incrementality.FULL_DATASET)
                    .setTimestamp(FEED_TIMESTAMP)
            )
            // every field present
            .addEntity(
                FeedEntity.newBuilder()
                    .setId("R-1")
                    .setVehicle(
                        VehiclePosition.newBuilder()
                            .setVehicle(VehicleDescriptor.newBuilder().setId("R-1").setLabel("1827"))
                            .setTrip(TripDescriptor.newBuilder().setTripId("77871166").setRouteId("Red").setDirectionId(1))
                            .setPosition(Position.newBuilder().setLatitude(42.30377f).setLongitude(-71.05578f).setBearing(195f).setSpeed(8.5f))
                            .setCurrentStatus(VehicleStopStatus.INCOMING_AT)
                            .setStopId("70089")
                            .setTimestamp(FEED_TIMESTAMP - 20)
                    )
            )
            // optional fields missing: no label, bearing, speed, status, stop or timestamp
            .addEntity(
                FeedEntity.newBuilder()
                    .setId("R-2")
                    .setVehicle(
                        VehiclePosition.newBuilder()
                            .setVehicle(VehicleDescriptor.newBuilder().setId("R-2"))
                            .setTrip(TripDescriptor.newBuilder().setRouteId("Red"))
                            .setPosition(Position.newBuilder().setLatitude(42.35f).setLongitude(-71.06f))
                    )
            )
            .addEntity(
                FeedEntity.newBuilder()
                    .setId("Y-39")
                    .setVehicle(
                        VehiclePosition.newBuilder()
                            .setVehicle(VehicleDescriptor.newBuilder().setId("Y-39").setLabel("1601"))
                            .setTrip(TripDescriptor.newBuilder().setTripId("70001").setRouteId("39"))
                            .setPosition(Position.newBuilder().setLatitude(42.3355f).setLongitude(-71.1039f))
                            .setCurrentStatus(VehicleStopStatus.STOPPED_AT)
                            .setStopId("1317")
                            .setTimestamp(FEED_TIMESTAMP - 5)
                    )
            )
            // deleted: must not be served
            .addEntity(
                FeedEntity.newBuilder()
                    .setId("R-DELETED")
                    .setIsDeleted(true)
                    .setVehicle(
                        VehiclePosition.newBuilder()
                            .setVehicle(VehicleDescriptor.newBuilder().setId("R-DELETED"))
                            .setTrip(TripDescriptor.newBuilder().setRouteId("Red"))
                    )
            )
            // not a vehicle (a service alert): must be ignored
            .addEntity(FeedEntity.newBuilder().setId("alert-1").setAlert(Alert.newBuilder()))
            .build();
    }

    public static byte[] vehiclePositionsBytes() {
        return vehiclePositions().toByteArray();
    }
}

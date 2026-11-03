package com.example.service.dto;

import com.example.service.dto.MbtaDtos.RouteDTO;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** The live route board: MBTA V3 reference data (route, station names) merged with GTFS-Realtime positions. */
public final class RouteBoardDtos {

    private RouteBoardDtos() {}

    /** Whether each upstream source contributed; the board is still returned when one of them is down. */
    public enum SourceStatus {
        OK,
        UNAVAILABLE,
    }

    public record RouteBoardDTO(RouteDTO route, Instant feedTimestamp, List<LiveVehicleDTO> vehicles, Map<String, SourceStatus> sources) {}

    public record LiveVehicleDTO(
        String vehicleId,
        String label,
        String status,
        Integer directionId,
        String destination,
        StopRefDTO stop,
        double latitude,
        double longitude,
        Float bearing,
        Instant observedAt
    ) {}

    /** {@code name} and {@code platform} are null when the V3 source was unavailable. */
    public record StopRefDTO(String id, String stationId, String name, String platform) {}
}

package com.example.service.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Our API's view of MBTA data: flat, camelCase, no JSON:API envelope. Mapped from the upstream model by MbtaService,
 * so changes to MBTA's API stay inside the client and the mapping.
 */
public final class MbtaDtos {

    private MbtaDtos() {}

    public record RouteDTO(
        String id,
        String name,
        String color,
        String textColor,
        String kind,
        List<DirectionDTO> directions
    ) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    /** {@code id} 0 or 1, as used by GTFS {@code direction_id} in both the V3 API and the realtime feed. */
    public record DirectionDTO(int id, String name, String destination) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    public record StationDTO(String id, String name, Double latitude, Double longitude, List<PlatformDTO> platforms) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    /** A boarding platform; {@code direction} is MBTA's platform name, e.g. "Alewife" or "Ashmont/Braintree". */
    public record PlatformDTO(String id, String direction) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    public record PredictionDTO(
        String routeId,
        String stopId,
        String vehicleId,
        String tripId,
        Integer directionId,
        OffsetDateTime arrivalTime,
        OffsetDateTime departureTime,
        String status
    ) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    public record AlertDTO(
        String id,
        String header,
        String effect,
        Integer severity,
        String lifecycle,
        OffsetDateTime activeFrom,
        OffsetDateTime activeUntil
    ) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }
}

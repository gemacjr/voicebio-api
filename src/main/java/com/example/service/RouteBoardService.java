package com.example.service;

import com.example.api.transit.v1.Vehicle;
import com.example.api.transit.v1.VehicleList;
import com.example.api.transit.v1.VehicleStatus;
import com.example.service.dto.MbtaDtos.DirectionDTO;
import com.example.service.dto.MbtaDtos.RouteDTO;
import com.example.service.dto.MbtaDtos.StationDTO;
import com.example.service.dto.RouteBoardDtos.LiveVehicleDTO;
import com.example.service.dto.RouteBoardDtos.RouteBoardDTO;
import com.example.service.dto.RouteBoardDtos.SourceStatus;
import com.example.service.dto.RouteBoardDtos.StopRefDTO;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.annotation.Observed;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

/**
 * Combines the two MBTA sources for one route:
 * <ul>
 *   <li>REST / JSON:API (MBTA V3): route name, colour, directions, and station + platform names</li>
 *   <li>Protocol Buffers (GTFS-Realtime): live vehicle positions</li>
 * </ul>
 * Both are fetched in parallel on virtual threads, each with its own time budget. If one source fails the board is
 * still returned, with {@code sources} saying what is missing; an unknown route is a 404; if both fail, a 502.
 * <p>
 * Observability: {@code @Observed} timer {@code route.board}; counter {@code route.board.degraded} (tag {@code source}).
 */
@Slf4j
@Service
@Observed(name = "route.board", contextualName = "route-board")
public class RouteBoardService {

    static final String V3 = "v3";
    static final String GTFS_REALTIME = "gtfsRealtime";
    static final Duration SOURCE_TIMEOUT = Duration.ofSeconds(4);
    private static final int ALL_VEHICLES = 1000;

    private final MbtaService mbta;
    private final TransitService transit;
    private final AsyncTaskExecutor executor;
    private final MeterRegistry meterRegistry;
    private final Duration sourceTimeout;

    @Autowired
    public RouteBoardService(
        MbtaService mbta,
        TransitService transit,
        @Qualifier("upstreamCallExecutor") AsyncTaskExecutor executor,
        MeterRegistry meterRegistry
    ) {
        this(mbta, transit, executor, meterRegistry, SOURCE_TIMEOUT);
    }

    RouteBoardService(MbtaService mbta, TransitService transit, AsyncTaskExecutor executor, MeterRegistry meterRegistry, Duration sourceTimeout) {
        this.mbta = mbta;
        this.transit = transit;
        this.executor = executor;
        this.meterRegistry = meterRegistry;
        this.sourceTimeout = sourceTimeout;
    }

    private record ReferenceData(RouteDTO route, List<StationDTO> stations) {}

    public RouteBoardDTO liveBoard(String routeId) {
        // start both calls before waiting on either: total time is the slower call, not the sum
        CompletableFuture<ReferenceData> reference = async(() -> new ReferenceData(mbta.route(routeId), mbta.stations(routeId)));
        CompletableFuture<VehicleList> positions = async(() -> transit.vehicles(routeId, ALL_VEHICLES));

        Map<String, SourceStatus> sources = new LinkedHashMap<>();
        ReferenceData referenceData = await(reference, V3, routeId, sources);
        VehicleList vehicleList = await(positions, GTFS_REALTIME, routeId, sources);
        if (referenceData == null && vehicleList == null) {
            throw new RouteBoardUnavailableException(routeId);
        }

        RouteDTO route = referenceData != null ? referenceData.route() : new RouteDTO(routeId, null, null, null, null, List.of());
        Map<String, StopRefDTO> stops = referenceData != null ? stopIndex(referenceData.stations()) : Map.of();
        List<LiveVehicleDTO> vehicles = vehicleList == null
            ? List.of()
            : vehicleList.getVehiclesList().stream().map(v -> toLiveVehicle(v, route, stops)).toList();
        Instant feedTimestamp = vehicleList == null || !vehicleList.hasFeedTimestamp()
            ? null
            : Instant.ofEpochSecond(vehicleList.getFeedTimestamp().getSeconds());

        log.info("Route board {}: {} vehicles, sources {}", routeId, vehicles.size(), sources);
        return new RouteBoardDTO(route, feedTimestamp, vehicles, sources);
    }

    private <T> CompletableFuture<T> async(Supplier<T> call) {
        return CompletableFuture.supplyAsync(call, executor).orTimeout(sourceTimeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** The source's result, or null (recorded as UNAVAILABLE) if it failed; an unknown route is rethrown as a 404. */
    private <T> T await(CompletableFuture<T> future, String source, String routeId, Map<String, SourceStatus> sources) {
        try {
            T result = future.join();
            sources.put(source, SourceStatus.OK);
            return result;
        } catch (CompletionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof RouteNotFoundException notFound) {
                throw notFound;
            }
            log.warn("Route board {}: source {} unavailable: {}", routeId, source, cause.toString());
            meterRegistry.counter("route.board.degraded", "source", source).increment();
            sources.put(source, SourceStatus.UNAVAILABLE);
            return null;
        }
    }

    /** Realtime data refers to platforms ("70104"); V3 knows them as child stops of stations ("Quincy Adams"). */
    static Map<String, StopRefDTO> stopIndex(List<StationDTO> stations) {
        Map<String, StopRefDTO> index = new HashMap<>();
        for (StationDTO station : stations) {
            station.platforms().forEach(p -> index.put(p.id(), new StopRefDTO(p.id(), station.id(), station.name(), p.direction())));
        }
        return index;
    }

    static LiveVehicleDTO toLiveVehicle(Vehicle v, RouteDTO route, Map<String, StopRefDTO> stops) {
        Integer directionId = v.hasDirectionId() ? v.getDirectionId() : null;
        String destination = directionId == null
            ? null
            : route.directions().stream().filter(d -> d.id() == directionId).map(DirectionDTO::destination).findFirst().orElse(null);
        StopRefDTO stop = v.getStopId().isEmpty() ? null : stops.getOrDefault(v.getStopId(), new StopRefDTO(v.getStopId(), null, null, null));
        return new LiveVehicleDTO(
            v.getVehicleId(),
            v.getLabel().isEmpty() ? null : v.getLabel(),
            v.getStatus() == VehicleStatus.VEHICLE_STATUS_UNSPECIFIED ? null : v.getStatus().name(),
            directionId,
            destination,
            stop,
            v.getLatitude(),
            v.getLongitude(),
            v.getBearing() == 0f ? null : v.getBearing(),
            v.hasObservedAt() ? Instant.ofEpochSecond(v.getObservedAt().getSeconds()) : null
        );
    }
}

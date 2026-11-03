package com.example.service;

import com.example.client.mbta.JsonApiResource;
import com.example.client.mbta.MbtaAttributes;
import com.example.client.mbta.MbtaNotFoundException;
import com.example.client.mbta.MbtaV3Client;
import com.example.service.dto.MbtaDtos.AlertDTO;
import com.example.service.dto.MbtaDtos.DirectionDTO;
import com.example.service.dto.MbtaDtos.PlatformDTO;
import com.example.service.dto.MbtaDtos.PredictionDTO;
import com.example.service.dto.MbtaDtos.RouteDTO;
import com.example.service.dto.MbtaDtos.StationDTO;
import io.micrometer.observation.annotation.Observed;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * MBTA V3 API (JSON:API) mapped to this API's DTOs, with caching sized to how often each kind of data changes.
 * <p>
 * Observability: {@code @Observed} timer {@code mbta.service} (tag {@code method}); cache statistics per cache.
 */
@Slf4j
@Service
@Observed(name = "mbta.service", contextualName = "mbta-service")
public class MbtaService {

    public static final String ROUTES_CACHE = "mbtaRoutes";
    public static final String STATIONS_CACHE = "mbtaStations";
    public static final String PREDICTIONS_CACHE = "mbtaPredictions";
    public static final String ALERTS_CACHE = "mbtaAlerts";

    /** GTFS route types 0 (light rail) and 1 (heavy rail): the subway. */
    public static final String SUBWAY = "0,1";
    private static final int PLATFORM = 0;

    private final MbtaV3Client client;

    public MbtaService(MbtaV3Client client) {
        this.client = client;
    }

    @Cacheable(cacheNames = ROUTES_CACHE, key = "'types:' + #types")
    public List<RouteDTO> routes(String types) {
        log.info("Fetching MBTA routes of types {}", types);
        return client.routes(types).data().stream().map(MbtaService::toRoute).toList();
    }

    @Cacheable(cacheNames = ROUTES_CACHE, key = "#routeId")
    public RouteDTO route(String routeId) {
        log.info("Fetching MBTA route {}", routeId);
        try {
            return toRoute(client.route(routeId).data());
        } catch (MbtaNotFoundException e) {
            throw new RouteNotFoundException(routeId);
        }
    }

    /** Parent stations of a route in MBTA's order, each with its platforms (the stop ids used by realtime data). */
    @Cacheable(cacheNames = STATIONS_CACHE, key = "#routeId")
    public List<StationDTO> stations(String routeId) {
        log.info("Fetching MBTA stations of route {}", routeId);
        var document = client.stationsWithPlatforms(routeId);
        if (document.data().isEmpty()) {
            route(routeId); // distinguishes "unknown route" (404) from "route without stations"
        }
        Map<String, JsonApiResource<MbtaAttributes.Stop>> platforms = document
            .included()
            .stream()
            .filter(s -> s.attributes() != null && Integer.valueOf(PLATFORM).equals(s.attributes().locationType()))
            .collect(Collectors.toMap(JsonApiResource::id, Function.identity(), (a, b) -> a));
        return document
            .data()
            .stream()
            .map(station -> {
                List<PlatformDTO> stationPlatforms = station.relationships() == null || !station.relationships().containsKey("child_stops")
                    ? List.of()
                    : station
                        .relationships()
                        .get("child_stops")
                        .ids()
                        .stream()
                        .map(platforms::get)
                        .filter(p -> p != null)
                        .map(p -> new PlatformDTO(p.id(), p.attributes().platformName()))
                        .sorted(Comparator.comparing(PlatformDTO::id))
                        .toList();
                MbtaAttributes.Stop a = station.attributes();
                return new StationDTO(station.id(), a.name(), a.latitude(), a.longitude(), stationPlatforms);
            })
            .toList();
    }

    @Cacheable(cacheNames = PREDICTIONS_CACHE, key = "#stopId + ':' + #routeId + ':' + #limit")
    public List<PredictionDTO> predictions(String stopId, String routeId, int limit) {
        log.info("Fetching MBTA predictions for stop {}{}", stopId, routeId == null ? "" : " on route " + routeId);
        return client
            .predictions(stopId, routeId, limit)
            .data()
            .stream()
            .map(p ->
                new PredictionDTO(
                    p.relatedId("route").orElse(null),
                    p.relatedId("stop").orElse(null),
                    p.relatedId("vehicle").orElse(null),
                    p.relatedId("trip").orElse(null),
                    p.attributes().directionId(),
                    p.attributes().arrivalTime(),
                    p.attributes().departureTime(),
                    p.attributes().status()
                )
            )
            .toList();
    }

    @Cacheable(cacheNames = ALERTS_CACHE, key = "#routeId")
    public List<AlertDTO> alerts(String routeId) {
        log.info("Fetching MBTA alerts for route {}", routeId);
        return client
            .alerts(routeId)
            .data()
            .stream()
            .map(a -> {
                var period = a.attributes().activePeriod() == null || a.attributes().activePeriod().isEmpty()
                    ? null
                    : a.attributes().activePeriod().getFirst();
                return new AlertDTO(
                    a.id(),
                    a.attributes().header(),
                    a.attributes().effect(),
                    a.attributes().severity(),
                    a.attributes().lifecycle(),
                    period == null ? null : period.start(),
                    period == null ? null : period.end()
                );
            })
            .toList();
    }

    static RouteDTO toRoute(JsonApiResource<MbtaAttributes.Route> route) {
        MbtaAttributes.Route a = route.attributes();
        List<String> names = a.directionNames() == null ? List.of() : a.directionNames();
        List<String> destinations = a.directionDestinations() == null ? List.of() : a.directionDestinations();
        List<DirectionDTO> directions = new ArrayList<>(
            IntStream.range(0, Math.min(names.size(), destinations.size())).mapToObj(i -> new DirectionDTO(i, names.get(i), destinations.get(i))).toList()
        );
        String name = a.longName() == null || a.longName().isBlank() ? a.shortName() : a.longName();
        return new RouteDTO(route.id(), name, a.color(), a.textColor(), kind(a.type()), List.copyOf(directions));
    }

    /** GTFS route_type as a readable value. */
    static String kind(Integer type) {
        if (type == null) return "UNKNOWN";
        return switch (type) {
            case 0 -> "LIGHT_RAIL";
            case 1 -> "SUBWAY";
            case 2 -> "COMMUTER_RAIL";
            case 3 -> "BUS";
            case 4 -> "FERRY";
            default -> "OTHER";
        };
    }
}

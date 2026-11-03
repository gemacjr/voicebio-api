package com.example.web.rest;

import com.example.service.MbtaService;
import com.example.service.dto.MbtaDtos.AlertDTO;
import com.example.service.dto.MbtaDtos.PredictionDTO;
import com.example.service.dto.MbtaDtos.RouteDTO;
import com.example.service.dto.MbtaDtos.StationDTO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only MBTA reference and realtime data from the MBTA V3 REST API, reshaped into this API's own JSON.
 * <p>
 * Path and query values are validated before they reach the upstream URL, cache keys and logs.
 */
@Slf4j
@RestController
@RequestMapping("/api/mbta")
public class MbtaController {

    private static final String ID = "[A-Za-z0-9._-]{1,64}";

    private final MbtaService mbtaService;

    public MbtaController(MbtaService mbtaService) {
        this.mbtaService = mbtaService;
    }

    /** {@code GET /api/mbta/routes?types=0,1}: routes of the given GTFS route types (default: subway). */
    @GetMapping("/routes")
    public List<RouteDTO> getRoutes(@RequestParam(defaultValue = MbtaService.SUBWAY) @Pattern(regexp = "[0-4](,[0-4])*") String types) {
        log.debug("REST request to get routes of types {}", types);
        return mbtaService.routes(types);
    }

    /** {@code GET /api/mbta/routes/Red} (404 for an unknown route). */
    @GetMapping("/routes/{routeId}")
    public RouteDTO getRoute(@PathVariable @Pattern(regexp = ID) String routeId) {
        log.debug("REST request to get route {}", routeId);
        return mbtaService.route(routeId);
    }

    /** {@code GET /api/mbta/routes/Red/stations}: stations in order, with their platforms. */
    @GetMapping("/routes/{routeId}/stations")
    public List<StationDTO> getStations(@PathVariable @Pattern(regexp = ID) String routeId) {
        log.debug("REST request to get stations of route {}", routeId);
        return mbtaService.stations(routeId);
    }

    /** {@code GET /api/mbta/routes/Red/alerts}: current service alerts. */
    @GetMapping("/routes/{routeId}/alerts")
    public List<AlertDTO> getAlerts(@PathVariable @Pattern(regexp = ID) String routeId) {
        log.debug("REST request to get alerts of route {}", routeId);
        return mbtaService.alerts(routeId);
    }

    /** {@code GET /api/mbta/stops/place-pktrm/predictions?routeId=Red&limit=5}: next arrivals, soonest first. */
    @GetMapping("/stops/{stopId}/predictions")
    public List<PredictionDTO> getPredictions(
        @PathVariable @Pattern(regexp = ID) String stopId,
        @RequestParam(required = false) @Pattern(regexp = ID) String routeId,
        @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit
    ) {
        log.debug("REST request to get predictions for stop {} route {}", stopId, routeId);
        return mbtaService.predictions(stopId, routeId, limit);
    }
}

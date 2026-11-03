package com.example.web.rest;

import com.example.service.RouteBoardService;
import com.example.service.dto.RouteBoardDtos.RouteBoardDTO;
import jakarta.validation.constraints.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Live route board: one call that combines the MBTA V3 REST API (names) with the GTFS-Realtime protobuf feed
 * (positions). See RouteBoardService for the parallel calls and partial-failure behaviour.
 */
@Slf4j
@RestController
@RequestMapping("/api/routes")
public class RouteBoardController {

    private final RouteBoardService routeBoardService;

    public RouteBoardController(RouteBoardService routeBoardService) {
        this.routeBoardService = routeBoardService;
    }

    /** {@code GET /api/routes/Red/live} */
    @GetMapping("/{routeId}/live")
    public RouteBoardDTO getLiveBoard(@PathVariable @Pattern(regexp = "[A-Za-z0-9._-]{1,64}") String routeId) {
        log.debug("REST request to get live board of route {}", routeId);
        return routeBoardService.liveBoard(routeId);
    }
}

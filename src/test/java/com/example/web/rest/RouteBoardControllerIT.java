package com.example.web.rest;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.IntegrationTest;
import com.example.client.transit.TransitFeedException;
import com.example.service.CachedVehicleFeed;
import com.example.service.MbtaService;
import com.example.service.RouteNotFoundException;
import com.example.service.dto.MbtaDtos.DirectionDTO;
import com.example.service.dto.MbtaDtos.PlatformDTO;
import com.example.service.dto.MbtaDtos.RouteDTO;
import com.example.service.dto.MbtaDtos.StationDTO;
import com.example.transit.GtfsFixtures;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The real RouteBoardService, executor and mapping; only the two upstream sources are mocked. */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class RouteBoardControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MbtaService mbtaService;

    @MockitoBean
    private CachedVehicleFeed feed;

    @BeforeEach
    void sources() {
        when(mbtaService.route("Red")).thenReturn(
            new RouteDTO("Red", "Red Line", "DA291C", "FFFFFF", "SUBWAY", List.of(new DirectionDTO(0, "South", "Ashmont/Braintree"), new DirectionDTO(1, "North", "Alewife")))
        );
        when(mbtaService.stations("Red")).thenReturn(
            List.of(new StationDTO("place-jfk", "JFK/UMass", 42.32, -71.05, List.of(new PlatformDTO("70089", "Alewife"))))
        );
        when(feed.vehiclePositions()).thenReturn(GtfsFixtures.vehiclePositions());
    }

    @Test
    void combinesBothSources() throws Exception {
        mockMvc
            .perform(get("/api/routes/Red/live"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.route.name").value("Red Line"))
            .andExpect(jsonPath("$.sources.v3").value("OK"))
            .andExpect(jsonPath("$.sources.gtfsRealtime").value("OK"))
            .andExpect(jsonPath("$.feedTimestamp").value("2026-11-03T20:49:59Z"))
            .andExpect(jsonPath("$.vehicles.length()").value(2))
            .andExpect(jsonPath("$.vehicles[0].vehicleId").value("R-1"))
            .andExpect(jsonPath("$.vehicles[0].stop.name").value("JFK/UMass"))
            .andExpect(jsonPath("$.vehicles[0].stop.platform").value("Alewife"))
            .andExpect(jsonPath("$.vehicles[0].destination").value("Alewife"));
    }

    @Test
    void degradesWhenTheFeedIsDown() throws Exception {
        when(feed.vehiclePositions()).thenThrow(new TransitFeedException("Transit feed returned HTTP 503 for /realtime/VehiclePositions.pb"));

        mockMvc
            .perform(get("/api/routes/Red/live"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sources.gtfsRealtime").value("UNAVAILABLE"))
            .andExpect(jsonPath("$.route.name").value("Red Line"))
            .andExpect(jsonPath("$.vehicles.length()").value(0));
    }

    @Test
    void unknownRouteIs404() throws Exception {
        when(mbtaService.route("Nope")).thenThrow(new RouteNotFoundException("Nope"));

        mockMvc.perform(get("/api/routes/Nope/live")).andExpect(status().isNotFound()).andExpect(jsonPath("$.title").value("Route not found"));
    }

    @Test
    void echoesTheRequestId() throws Exception {
        mockMvc
            .perform(get("/api/routes/Red/live").header("X-Request-Id", "board-it-1"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Request-Id", "board-it-1"));
    }

    @Test
    @WithUnauthenticatedMockUser
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/routes/Red/live")).andExpect(status().isUnauthorized());
    }
}

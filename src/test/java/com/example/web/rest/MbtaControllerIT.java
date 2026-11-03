package com.example.web.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.IntegrationTest;
import com.example.client.mbta.MbtaApiException;
import com.example.client.mbta.MbtaRateLimitedException;
import com.example.service.MbtaService;
import com.example.service.RouteNotFoundException;
import com.example.service.dto.MbtaDtos.DirectionDTO;
import com.example.service.dto.MbtaDtos.PredictionDTO;
import com.example.service.dto.MbtaDtos.RouteDTO;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** MVC, validation, security and error handling of {@link MbtaController}, with {@link MbtaService} mocked. */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class MbtaControllerIT {

    private static final RouteDTO RED = new RouteDTO("Red", "Red Line", "DA291C", "FFFFFF", "SUBWAY", List.of(new DirectionDTO(1, "North", "Alewife")));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MbtaService mbtaService;

    @Test
    void routesDefaultToTheSubway() throws Exception {
        when(mbtaService.routes(MbtaService.SUBWAY)).thenReturn(List.of(RED));

        mockMvc
            .perform(get("/api/mbta/routes"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("Red"))
            .andExpect(jsonPath("$[0].directions[0].destination").value("Alewife"));
    }

    @Test
    void invalidRouteTypesAreRejected() throws Exception {
        mockMvc.perform(get("/api/mbta/routes").param("types", "0;DROP")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("types"));
        verifyNoInteractions(mbtaService);
    }

    @Test
    void unknownRouteIsA404Problem() throws Exception {
        when(mbtaService.route("Nope")).thenThrow(new RouteNotFoundException("Nope"));

        mockMvc
            .perform(get("/api/mbta/routes/Nope"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").value("Route not found"))
            .andExpect(jsonPath("$.detail").value("Route Nope does not exist"));
    }

    @Test
    void pathValuesAreValidatedBeforeReachingUpstream() throws Exception {
        mockMvc.perform(get("/api/mbta/routes/{id}", "Red Line!")).andExpect(status().isBadRequest());
        verifyNoInteractions(mbtaService);
    }

    @Test
    void predictionsLimitIsBounded() throws Exception {
        mockMvc
            .perform(get("/api/mbta/stops/place-pktrm/predictions").param("limit", "51"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("limit"));
    }

    @Test
    void predictions() throws Exception {
        when(mbtaService.predictions("place-pktrm", "Red", 3)).thenReturn(
            List.of(new PredictionDTO("Red", "70076", "R-1", "t-1", 1, OffsetDateTime.parse("2026-11-03T16:13:14-05:00"), null, null))
        );

        mockMvc
            .perform(get("/api/mbta/stops/place-pktrm/predictions").param("routeId", "Red").param("limit", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].vehicleId").value("R-1"))
            .andExpect(jsonPath("$[0].arrivalTime").value("2026-11-03T16:13:14-05:00"));
    }

    @Test
    void upstreamRateLimitIs503WithRetryAfter() throws Exception {
        when(mbtaService.route("Red")).thenThrow(new MbtaRateLimitedException(42));

        mockMvc
            .perform(get("/api/mbta/routes/Red"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(header().string("Retry-After", "42"))
            .andExpect(jsonPath("$.title").value("MBTA API rate limited"))
            .andExpect(jsonPath("$.detail").value("MBTA API rate limit reached, retry in 42 s"));
    }

    @Test
    void upstreamErrorIs502() throws Exception {
        when(mbtaService.alerts(any())).thenThrow(new MbtaApiException("MBTA API returned HTTP 500 for GET /alerts"));

        mockMvc.perform(get("/api/mbta/routes/Red/alerts")).andExpect(status().isBadGateway()).andExpect(jsonPath("$.title").value("MBTA API error"));
    }

    @Test
    @WithUnauthenticatedMockUser
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/mbta/routes")).andExpect(status().isUnauthorized());
    }
}

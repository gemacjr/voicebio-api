package com.example.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.IntegrationTest;
import com.example.api.transit.v1.VehicleList;
import com.example.client.transit.TransitFeedException;
import com.example.service.CachedVehicleFeed;
import com.example.transit.GtfsFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Content negotiation, errors and security of {@link TransitController}; the real mapping, a mocked feed. */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class TransitControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CachedVehicleFeed feed;

    @BeforeEach
    void feed() {
        when(feed.vehiclePositions()).thenReturn(GtfsFixtures.vehiclePositions());
    }

    @Test
    void jsonUsesProtobufCanonicalMapping() throws Exception {
        mockMvc
            .perform(get("/api/transit/vehicles").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.totalInFeed").value(3))
            .andExpect(jsonPath("$.feedTimestamp").value("2026-11-03T20:49:59Z"))
            .andExpect(jsonPath("$.vehicles[0].vehicleId").value("R-1"))
            .andExpect(jsonPath("$.vehicles[0].speedMetersPerSecond").value(8.5))
            .andExpect(jsonPath("$.vehicles[0].status").value("INCOMING_AT"))
            .andExpect(jsonPath("$.vehicles[1].label").doesNotExist()); // unset proto3 fields are omitted
    }

    @Test
    void binaryProtobufDecodesWithTheGeneratedClass() throws Exception {
        byte[] body = mockMvc
            .perform(get("/api/transit/vehicles").param("routeId", "Red").accept(MediaType.APPLICATION_PROTOBUF))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROTOBUF))
            .andExpect(header().string("X-Protobuf-Message", "voicebio.transit.v1.VehicleList"))
            .andExpect(header().string("X-Protobuf-Schema", "voicebio/transit/v1/transit.proto"))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

        VehicleList list = VehicleList.parseFrom(body);
        assertThat(list.getVehiclesCount()).isEqualTo(2);
        assertThat(list.getVehicles(0).getRouteId()).isEqualTo("Red");
    }

    @Test
    void noAcceptHeaderGetsJson() throws Exception {
        mockMvc
            .perform(get("/api/transit/vehicles"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void singleVehicle() throws Exception {
        mockMvc
            .perform(get("/api/transit/vehicles/Y-39").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.routeId").value("39"))
            .andExpect(jsonPath("$.status").value("STOPPED_AT"));
    }

    @Test
    void errorsStayReadableWhenProtobufWasRequested() throws Exception {
        mockMvc
            .perform(get("/api/transit/vehicles/nope").accept(MediaType.APPLICATION_PROTOBUF))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Vehicle not found"));
    }

    @Test
    void invalidLimitListsTheParameter() throws Exception {
        mockMvc
            .perform(get("/api/transit/vehicles").param("limit", "0").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.validation"))
            .andExpect(jsonPath("$.detail").value("Invalid request parameters"))
            .andExpect(jsonPath("$.fieldErrors[0].field").value("limit"))
            .andExpect(jsonPath("$.fieldErrors[0].message").value("must be greater than or equal to 1"));
    }

    @Test
    void upstreamFailureIsBadGateway() throws Exception {
        when(feed.vehiclePositions()).thenThrow(new TransitFeedException("Transit feed returned HTTP 503 for /realtime/VehiclePositions.pb"));

        mockMvc
            .perform(get("/api/transit/vehicles").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.title").value("Transit feed error"));
    }

    @Test
    @WithUnauthenticatedMockUser
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/transit/vehicles")).andExpect(status().isUnauthorized());
    }
}

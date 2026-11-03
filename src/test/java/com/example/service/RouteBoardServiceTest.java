package com.example.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.api.transit.v1.VehicleList;
import com.example.client.mbta.MbtaApiException;
import com.example.client.transit.TransitFeedException;
import com.example.service.dto.MbtaDtos.DirectionDTO;
import com.example.service.dto.MbtaDtos.PlatformDTO;
import com.example.service.dto.MbtaDtos.RouteDTO;
import com.example.service.dto.MbtaDtos.StationDTO;
import com.example.service.dto.RouteBoardDtos.LiveVehicleDTO;
import com.example.service.dto.RouteBoardDtos.SourceStatus;
import com.example.service.dto.RouteBoardDtos.StopRefDTO;
import com.example.transit.GtfsFixtures;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

/** Combining the V3 REST source and the GTFS-Realtime protobuf source, including partial failures. */
class RouteBoardServiceTest {

    private static final RouteDTO RED = new RouteDTO(
        "Red",
        "Red Line",
        "DA291C",
        "FFFFFF",
        "SUBWAY",
        List.of(new DirectionDTO(0, "South", "Ashmont/Braintree"), new DirectionDTO(1, "North", "Alewife"))
    );
    private static final List<StationDTO> STATIONS = List.of(
        new StationDTO("place-jfk", "JFK/UMass", 42.32, -71.05, List.of(new PlatformDTO("70085", "Ashmont/Braintree"), new PlatformDTO("70089", "Alewife")))
    );

    private final MbtaService mbta = mock(MbtaService.class);
    private final CachedVehicleFeed feed = mock(CachedVehicleFeed.class);
    private SimpleMeterRegistry meterRegistry;
    private RouteBoardService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        TransitService transit = new TransitService(feed, meterRegistry, Clock.fixed(Instant.ofEpochSecond(GtfsFixtures.FEED_TIMESTAMP), ZoneOffset.UTC));
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("test-upstream-");
        executor.setVirtualThreads(true);
        service = new RouteBoardService(mbta, transit, executor, meterRegistry, Duration.ofMillis(500));
        when(mbta.route("Red")).thenReturn(RED);
        when(mbta.stations("Red")).thenReturn(STATIONS);
        when(feed.vehiclePositions()).thenReturn(GtfsFixtures.vehiclePositions());
    }

    @Test
    void mergesNamesFromV3WithPositionsFromTheFeed() {
        var board = service.liveBoard("Red");

        assertThat(board.sources()).containsExactly(Map.entry("v3", SourceStatus.OK), Map.entry("gtfsRealtime", SourceStatus.OK));
        assertThat(board.route()).isEqualTo(RED);
        assertThat(board.feedTimestamp()).isEqualTo(Instant.ofEpochSecond(GtfsFixtures.FEED_TIMESTAMP));
        assertThat(board.vehicles()).extracting(LiveVehicleDTO::vehicleId).containsExactly("R-1", "R-2");

        LiveVehicleDTO r1 = board.vehicles().getFirst();
        assertThat(r1.stop()).isEqualTo(new StopRefDTO("70089", "place-jfk", "JFK/UMass", "Alewife")); // platform id -> station name
        assertThat(r1.directionId()).isEqualTo(1);
        assertThat(r1.destination()).isEqualTo("Alewife"); // direction_id from protobuf -> destination from V3
        assertThat(r1.status()).isEqualTo("INCOMING_AT");
    }

    @Test
    void vehiclesWithoutOptionalDataStayNull() {
        LiveVehicleDTO r2 = service.liveBoard("Red").vehicles().get(1);

        assertThat(r2.stop()).isNull();
        assertThat(r2.directionId()).isNull();
        assertThat(r2.destination()).isNull();
        assertThat(r2.bearing()).isNull();
    }

    @Test
    void feedDownStillReturnsTheRoute() {
        when(feed.vehiclePositions()).thenThrow(new TransitFeedException("Transit feed returned HTTP 503"));

        var board = service.liveBoard("Red");

        assertThat(board.sources()).containsEntry("gtfsRealtime", SourceStatus.UNAVAILABLE).containsEntry("v3", SourceStatus.OK);
        assertThat(board.route().name()).isEqualTo("Red Line");
        assertThat(board.vehicles()).isEmpty();
        assertThat(meterRegistry.get("route.board.degraded").tag("source", "gtfsRealtime").counter().count()).isEqualTo(1.0);
    }

    @Test
    void v3DownStillReturnsVehiclesWithoutNames() {
        when(mbta.stations("Red")).thenThrow(new MbtaApiException("MBTA API returned HTTP 500"));

        var board = service.liveBoard("Red");

        assertThat(board.sources()).containsEntry("v3", SourceStatus.UNAVAILABLE).containsEntry("gtfsRealtime", SourceStatus.OK);
        assertThat(board.route().id()).isEqualTo("Red");
        assertThat(board.route().name()).isNull();
        assertThat(board.vehicles().getFirst().stop()).isEqualTo(new StopRefDTO("70089", null, null, null));
    }

    @Test
    void slowSourceTimesOutInsteadOfHoldingTheRequest() {
        when(mbta.stations("Red")).thenAnswer(invocation -> {
            Thread.sleep(3_000);
            return STATIONS;
        });

        long start = System.nanoTime();
        var board = service.liveBoard("Red");

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
        assertThat(board.sources()).containsEntry("v3", SourceStatus.UNAVAILABLE);
        assertThat(board.vehicles()).hasSize(2);
    }

    @Test
    void bothDownIsBadGateway() {
        when(mbta.route("Red")).thenThrow(new MbtaApiException("down"));
        when(feed.vehiclePositions()).thenThrow(new TransitFeedException("down"));

        assertThatThrownBy(() -> service.liveBoard("Red")).isInstanceOf(RouteBoardUnavailableException.class);
    }

    @Test
    void unknownRouteIsNotFoundEvenIfTheFeedAnswers() {
        when(mbta.route("Nope")).thenThrow(new RouteNotFoundException("Nope"));

        assertThatThrownBy(() -> service.liveBoard("Nope")).isInstanceOf(RouteNotFoundException.class);
    }

    @Test
    void callsRunInParallel() {
        when(mbta.stations("Red")).thenAnswer(invocation -> {
            Thread.sleep(300);
            return STATIONS;
        });
        when(feed.vehiclePositions()).thenAnswer(invocation -> {
            Thread.sleep(300);
            return GtfsFixtures.vehiclePositions();
        });

        long start = System.nanoTime();
        var board = service.liveBoard("Red");

        assertThat(board.sources()).containsValues(SourceStatus.OK, SourceStatus.OK);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(550)); // sequential would be >= 600 ms
    }
}

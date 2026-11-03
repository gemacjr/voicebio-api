package com.example.client.mbta;

import com.example.client.mbta.MbtaAttributes.Alert;
import com.example.client.mbta.MbtaAttributes.Prediction;
import com.example.client.mbta.MbtaAttributes.Route;
import com.example.client.mbta.MbtaAttributes.Stop;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * Declarative client for the MBTA V3 API (https://api-v3.mbta.com), a JSON:API REST service.
 * <p>
 * JSON:API query parameters use brackets ({@code filter[route]=Red}); they are ordinary {@code @RequestParam} names.
 * Base URL and timeouts come from {@code spring.http.serviceclient.mbta.*}; the optional API key, the rate-limit
 * gauge and error mapping are added in {@code MbtaV3ClientConfiguration}.
 */
@HttpExchange(accept = MbtaV3Client.JSON_API)
public interface MbtaV3Client {
    String GROUP = "mbta";
    String JSON_API = "application/vnd.api+json";

    /** Routes of the given GTFS route types, e.g. "0,1" = light rail + heavy rail (the subway). */
    @GetExchange("/routes")
    JsonApiDocument<Route> routes(@RequestParam("filter[type]") String types);

    @GetExchange("/routes/{id}")
    JsonApiSingle<Route> route(@PathVariable String id);

    /** Parent stations served by a route, with their platforms in {@code included} (location_type 0). */
    @GetExchange("/stops?include=child_stops&fields[stop]=name,platform_name,location_type,latitude,longitude")
    JsonApiDocument<Stop> stationsWithPlatforms(@RequestParam("filter[route]") String routeId);

    @GetExchange("/predictions?sort=arrival_time")
    JsonApiDocument<Prediction> predictions(
        @RequestParam("filter[stop]") String stopId,
        @RequestParam(name = "filter[route]", required = false) String routeId,
        @RequestParam("page[limit]") int limit
    );

    @GetExchange("/alerts?fields[alert]=header,effect,severity,lifecycle,active_period")
    JsonApiDocument<Alert> alerts(@RequestParam("filter[route]") String routeId);
}

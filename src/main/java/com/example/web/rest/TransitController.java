package com.example.web.rest;

import com.example.api.transit.v1.Vehicle;
import com.example.api.transit.v1.VehicleList;
import com.example.service.TransitService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Live transit vehicle positions, returned as Protocol Buffers messages ({@code transit.proto}).
 *
 * <p>Content negotiation picks the wire format from the {@code Accept} header:
 *
 * <ul>
 *   <li>{@code application/x-protobuf}: compact binary protobuf
 *   <li>{@code application/json} (or no preference): the same message in protobuf's canonical JSON
 *       mapping
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping(
    value = "/api/transit",
    produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROTOBUF_VALUE})
public class TransitController {

  private final TransitService transitService;

  public TransitController(TransitService transitService) {
    this.transitService = transitService;
  }

  /** {@code GET /api/transit/vehicles?routeId=Red&limit=50} */
  @GetMapping("/vehicles")
  public VehicleList getVehicles(
      @RequestParam(required = false) String routeId,
      @RequestParam(defaultValue = "100") @Min(1) @Max(1000) int limit) {
    log.debug("REST request to get vehicles, routeId={}, limit={}", routeId, limit);
    return transitService.vehicles(routeId, limit);
  }

  /**
   * {@code GET /api/transit/vehicles/{vehicleId}} (404 when the vehicle is not in the current feed)
   */
  @GetMapping("/vehicles/{vehicleId}")
  public Vehicle getVehicle(@PathVariable String vehicleId) {
    log.debug("REST request to get vehicle {}", vehicleId);
    return transitService.vehicle(vehicleId);
  }
}

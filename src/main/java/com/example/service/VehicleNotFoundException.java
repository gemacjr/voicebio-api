package com.example.service;

import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** No vehicle with this id is in the current feed. Translated to HTTP 404. */
@ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "Vehicle not found")
public class VehicleNotFoundException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public VehicleNotFoundException(String vehicleId) {
    super("Vehicle " + vehicleId + " is not in the current feed");
  }
}

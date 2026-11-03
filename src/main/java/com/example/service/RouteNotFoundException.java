package com.example.service;

import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** MBTA has no route with this id. Translated to HTTP 404. */
@ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "Route not found")
public class RouteNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public RouteNotFoundException(String routeId) {
        super("Route " + routeId + " does not exist");
    }
}

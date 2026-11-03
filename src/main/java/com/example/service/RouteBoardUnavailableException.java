package com.example.service;

import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Neither upstream source of the route board answered. Translated to HTTP 502. */
@ResponseStatus(value = HttpStatus.BAD_GATEWAY, reason = "Route board unavailable")
public class RouteBoardUnavailableException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public RouteBoardUnavailableException(String routeId) {
        super("No upstream source answered for route " + routeId);
    }
}

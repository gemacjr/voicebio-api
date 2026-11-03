package com.example.client.transit;

import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** The GTFS-Realtime feed answered with an error. Translated to HTTP 502 when it reaches a controller. */
@ResponseStatus(value = HttpStatus.BAD_GATEWAY, reason = "Transit feed error")
public class TransitFeedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public TransitFeedException(String message) {
        super(message);
    }
}

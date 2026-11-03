package com.example.client.mbta;

import java.io.Serial;

/** MBTA answered 404. Services translate it into their own "not found"; untranslated, it is a 502 like any other error. */
public class MbtaNotFoundException extends MbtaApiException {

    @Serial
    private static final long serialVersionUID = 1L;

    public MbtaNotFoundException(String message) {
        super(message);
    }
}

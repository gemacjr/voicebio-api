package com.example.client.mbta;

import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** The MBTA V3 API answered with an error. Translated to HTTP 502 when it reaches a controller. */
@ResponseStatus(value = HttpStatus.BAD_GATEWAY, reason = "MBTA API error")
public class MbtaApiException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public MbtaApiException(String message) {
    super(message);
  }
}

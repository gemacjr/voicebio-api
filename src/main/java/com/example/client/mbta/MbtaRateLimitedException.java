package com.example.client.mbta;

import com.example.RetryableFailure;
import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * MBTA rejected the call with 429 (rate limit: 20 requests/minute without an API key).
 *
 * <p>Translated to 503 Service Unavailable, not 429: our caller did not exceed any limit of ours, a
 * dependency is temporarily unavailable. The {@code Retry-After} header tells the caller when to
 * try again.
 */
@ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE, reason = "MBTA API rate limited")
public class MbtaRateLimitedException extends RuntimeException implements RetryableFailure {

  @Serial private static final long serialVersionUID = 1L;

  private final long retryAfterSeconds;

  public MbtaRateLimitedException(long retryAfterSeconds) {
    super("MBTA API rate limit reached, retry in " + retryAfterSeconds + " s");
    this.retryAfterSeconds = retryAfterSeconds;
  }

  @Override
  public long retryAfterSeconds() {
    return retryAfterSeconds;
  }
}

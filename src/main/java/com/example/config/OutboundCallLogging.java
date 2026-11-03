package com.example.config;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/** Shared request interceptor: one log line per outbound HTTP call, with status and duration. */
@Slf4j
final class OutboundCallLogging {

  private OutboundCallLogging() {}

  static ClientHttpRequestInterceptor timingLog(String apiName) {
    return (request, body, execution) -> {
      long start = System.nanoTime();
      try {
        ClientHttpResponse response = execution.execute(request, body);
        log.info(
            "{} {} {} -> {} ({} ms)",
            apiName,
            request.getMethod(),
            request.getURI(),
            response.getStatusCode().value(),
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        return response;
      } catch (IOException e) {
        log.warn(
            "{} {} {} failed after {} ms: {}",
            apiName,
            request.getMethod(),
            request.getURI(),
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start),
            e.getMessage());
        throw e;
      }
    };
  }
}

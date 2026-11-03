package com.example.web.filter;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

  private static final String UUID_REGEX =
      "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

  private final RequestIdFilter filter = new RequestIdFilter();

  /** Runs the filter and returns the MDC request id seen by the rest of the chain. */
  private String requestIdSeenDownstream(
      MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
    AtomicReference<String> seen = new AtomicReference<>();
    MockFilterChain chain =
        new MockFilterChain() {
          @Override
          public void doFilter(
              jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
            seen.set(MDC.get(RequestIdFilter.MDC_KEY));
          }
        };
    filter.doFilter(request, response, chain);
    return seen.get();
  }

  @Test
  void generatesIdWhenHeaderMissing() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    String requestId = requestIdSeenDownstream(new MockHttpServletRequest(), response);

    assertThat(requestId).matches(UUID_REGEX);
    assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo(requestId);
  }

  @Test
  void reusesWellFormedIncomingId() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(RequestIdFilter.HEADER, "bruno-demo_1.2");
    MockHttpServletResponse response = new MockHttpServletResponse();

    assertThat(requestIdSeenDownstream(request, response)).isEqualTo("bruno-demo_1.2");
    assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("bruno-demo_1.2");
  }

  @Test
  void replacesIdThatCouldForgeLogLines() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(RequestIdFilter.HEADER, "abc\n2026-01-01 ERROR fake log entry");

    assertThat(requestIdSeenDownstream(request, new MockHttpServletResponse())).matches(UUID_REGEX);
  }

  @Test
  void replacesOverlongId() {
    assertThat(RequestIdFilter.resolveRequestId("a".repeat(65))).matches(UUID_REGEX);
    assertThat(RequestIdFilter.resolveRequestId("a".repeat(64))).isEqualTo("a".repeat(64));
  }

  @Test
  void clearsMdcAfterRequest() throws Exception {
    requestIdSeenDownstream(new MockHttpServletRequest(), new MockHttpServletResponse());

    assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
  }

  @Test
  void clearsMdcEvenWhenChainThrows() {
    MockFilterChain failingChain =
        new MockFilterChain() {
          @Override
          public void doFilter(
              jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
            throw new IllegalStateException("boom");
          }
        };
    HttpServletRequest request = new MockHttpServletRequest();
    HttpServletResponse response = new MockHttpServletResponse();

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> filter.doFilter(request, response, failingChain))
        .hasMessage("boom");
    assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
  }
}

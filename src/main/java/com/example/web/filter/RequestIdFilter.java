package com.example.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request a correlation id, stored in the SLF4J {@link MDC} under {@value #MDC_KEY}
 * so that every log line written while handling the request carries it (see {@code logback-spring.xml}).
 * <p>
 * The id is taken from the incoming {@value #HEADER} header when present and well-formed (so a gateway or
 * caller can correlate across services), otherwise a new UUID is generated. It is echoed back in the response.
 * <p>
 * Runs first, before Spring Security, so that even rejected (401/403) requests are correlated.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    /**
     * Incoming ids end up in every log line, and MDC values are not CRLF-sanitized like log messages are,
     * so only short ids made of safe characters are trusted. Anything else is replaced, never logged.
     */
    private static final Pattern VALID_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(HEADER));
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            // Tomcat reuses threads: a leftover id would be attached to the next, unrelated request
            MDC.remove(MDC_KEY);
        }
    }

    static String resolveRequestId(String incoming) {
        if (incoming != null && VALID_REQUEST_ID.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }
}

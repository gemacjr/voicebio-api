package com.example;

/**
 * Implemented by exceptions that mean "temporarily unavailable, try again later". The error handler turns
 * {@link #retryAfterSeconds()} into a {@code Retry-After} response header.
 * <p>
 * In the root package (like {@link SpringProfiles}) so the client layer can throw such exceptions and the web layer
 * can read them without depending on each other.
 */
public interface RetryableFailure {
    long retryAfterSeconds();
}

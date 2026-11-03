package com.example;

/**
 * Names of the Spring profiles the application knows about.
 *
 * <p>Kept in the root package (not {@code config}) so that every layer may reference it.
 */
public final class SpringProfiles {

  public static final String DEVELOPMENT = "dev";
  public static final String PRODUCTION = "prod";
  public static final String CLOUD = "cloud";
  public static final String API_DOCS = "api-docs";

  private SpringProfiles() {}
}

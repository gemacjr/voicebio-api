package com.example.web.rest;

import static com.example.security.SecurityUtils.AUTHORITIES_CLAIM;
import static com.example.security.SecurityUtils.JWT_ALGORITHM;

import com.example.SpringProfiles;
import com.example.security.AuthoritiesConstants;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DEV PROFILE ONLY. Issues a signed JWT without checking any credentials, so the API can be
 * exercised locally (Bruno, Gatling, curl). This bean does not exist in prod; in production, tokens
 * come from the gateway / identity provider.
 */
@Slf4j
@RestController
@RequestMapping("/api")
@Profile(SpringProfiles.DEVELOPMENT)
public class DevTokenController {

  private static final Duration TOKEN_VALIDITY = Duration.ofHours(1);
  private static final List<String> DEFAULT_AUTHORITIES =
      List.of(AuthoritiesConstants.USER, AuthoritiesConstants.ADMIN);

  private final JwtEncoder jwtEncoder;

  public DevTokenController(JwtEncoder jwtEncoder) {
    this.jwtEncoder = jwtEncoder;
  }

  /**
   * {@code POST /api/authenticate} : body {@code {"username": "...", "authorities":
   * ["ROLE_USER"]}}, both optional.
   */
  @PostMapping("/authenticate")
  public TokenResponse authenticate(@RequestBody(required = false) TokenRequest request) {
    String username =
        request != null && StringUtils.hasText(request.username()) ? request.username() : "demo";
    List<String> authorities =
        request != null && request.authorities() != null && !request.authorities().isEmpty()
            ? request.authorities()
            : DEFAULT_AUTHORITIES;

    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(username)
            .issuedAt(now)
            .expiresAt(now.plus(TOKEN_VALIDITY))
            .claim(AUTHORITIES_CLAIM, authorities)
            .build();
    String token =
        jwtEncoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(JWT_ALGORITHM).build(), claims))
            .getTokenValue();

    log.warn("Issued DEV-ONLY token for '{}' with authorities {}", username, authorities);
    return new TokenResponse(token, "Bearer", TOKEN_VALIDITY.toSeconds());
  }

  public record TokenRequest(String username, List<String> authorities) {}

  public record TokenResponse(
      @JsonProperty("id_token") String idToken,
      @JsonProperty("token_type") String tokenType,
      @JsonProperty("expires_in") long expiresIn) {
    /** Keep the token itself out of logs (e.g. LoggingAspect's exit trace). */
    @Override
    public String toString() {
      return "TokenResponse[tokenType=" + tokenType + ", expiresIn=" + expiresIn + ", idToken=***]";
    }
  }
}

package gatling.simulations;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.rampUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Load test for the MBTA endpoints. Requires the app running with the dev profile.
 *
 * <pre>
 * ./mvnw gatling:test -Dgatling.simulationClass=gatling.simulations.MbtaSimulation -Dusers=20 -Dramp=10
 * </pre>
 *
 * Every request is served from caches after warm-up (routes and stations: 1 h, realtime feed: 15
 * s), so a whole run makes only a handful of calls to MBTA and stays inside its 20 requests/minute
 * limit (no API key needed). Compare "Route board" (two sources merged) with "Route" (one cached
 * lookup) in the Gatling report.
 */
public class MbtaSimulation extends Simulation {

  private static final String BASE_URL = System.getProperty("baseURL", "http://localhost:8081");
  private static final int USERS = Integer.getInteger("users", 10);
  private static final int RAMP_SECONDS = Integer.getInteger("ramp", 10);
  private static final List<String> ROUTES = List.of("Red", "Orange", "Blue");

  private final HttpProtocolBuilder httpProtocol =
      http.baseUrl(BASE_URL)
          .acceptHeader("application/json")
          .contentTypeHeader("application/json")
          .userAgentHeader("Gatling/voicebio-api");

  private final ChainBuilder authenticate =
      exec(
          http("Authenticate")
              .post("/api/authenticate")
              .body(StringBody("{\"username\":\"gatling\"}"))
              .check(status().is(200))
              .check(jsonPath("$.id_token").saveAs("token")));

  private final ChainBuilder useApi =
      exec(http("Subway routes")
              .get("/api/mbta/routes")
              .header("Authorization", "Bearer #{token}")
              .check(status().is(200)))
          .pause(Duration.ofMillis(300))
          .repeat(3)
          .on(
              exec(session ->
                      session.set(
                          "routeId",
                          ROUTES.get(ThreadLocalRandom.current().nextInt(ROUTES.size()))))
                  .exec(
                      http("Route")
                          .get("/api/mbta/routes/#{routeId}")
                          .header("Authorization", "Bearer #{token}")
                          .check(status().is(200)))
                  .exec(
                      http("Route board")
                          .get("/api/routes/#{routeId}/live")
                          .header("Authorization", "Bearer #{token}")
                          .check(status().is(200))
                          .check(jsonPath("$.sources.v3").is("OK")))
                  .pause(Duration.ofMillis(200)))
          .exec(
              http("Transit vehicles (protobuf)")
                  .get("/api/transit/vehicles?limit=50")
                  .headers(
                      Map.of(
                          "Authorization", "Bearer #{token}", "Accept", "application/x-protobuf"))
                  .check(status().is(200)));

  private final ScenarioBuilder apiUsers = scenario("MBTA API users").exec(authenticate, useApi);

  {
    setUp(apiUsers.injectOpen(rampUsers(USERS).during(Duration.ofSeconds(RAMP_SECONDS))))
        .protocols(httpProtocol)
        .assertions(global().failedRequests().percent().lt(1.0));
  }
}

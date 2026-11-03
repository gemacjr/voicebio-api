# Voicebio API

[![CI](https://github.com/gemacjr/voicebio-api/actions/workflows/ci.yml/badge.svg)](https://github.com/gemacjr/voicebio-api/actions/workflows/ci.yml)

Spring Boot 4 REST microservice on **Java 21**. A stateless JWT resource server meant to sit behind a gateway, serving
Boston (MBTA) public transit data from a REST API and a Protocol Buffers feed.

📖 **Design guide:** [`docs/`](docs/index.md) explains how this service is built and the Spring Boot practices behind
it (architecture, REST and protobuf clients, combining sources, errors, security, logging and MDC, metrics, Splunk /
Fluentd / Prometheus / CloudWatch / Dynatrace, performance, testing incl. Karate, build and Docker), with a step-by-step
demo script. It is published with GitHub Pages from the `/docs` folder.

## Endpoints

| Endpoint | Source | Notes |
|---|---|---|
| `GET /api/mbta/routes?types=0,1` | MBTA V3 REST (JSON:API) | Subway routes by default; cached 1 h |
| `GET /api/mbta/routes/{routeId}` | MBTA V3 | 404 for an unknown route |
| `GET /api/mbta/routes/{routeId}/stations` | MBTA V3 | Stations with their platforms |
| `GET /api/mbta/routes/{routeId}/alerts` | MBTA V3 | Service alerts; cached 15 s |
| `GET /api/mbta/stops/{stopId}/predictions?routeId=&limit=` | MBTA V3 | Next arrivals; cached 15 s |
| `GET /api/transit/vehicles?routeId=&limit=` | GTFS-Realtime (protobuf) | `Accept: application/x-protobuf` or JSON |
| `GET /api/transit/vehicles/{vehicleId}` | GTFS-Realtime | |
| `GET /api/routes/{routeId}/live` | **Both, in parallel** | Live positions with station names; partial result if one source fails |

All endpoints need a JWT. With the `dev` profile, `POST /api/authenticate` issues one (dev only, never in prod).

Without an API key MBTA allows 20 requests/minute; the app caches to stay well below. Set `MBTA_API_KEY` (free from
MBTA) to raise the limit to 1,000.

## Project structure

- `src/main/java` - application code (`com.example`)
- `src/main/proto` - Protocol Buffers schemas (vendored GTFS-Realtime, our `transit.proto`), compiled at build time
- `src/main/resources/config` - Spring configuration per profile (`application-dev.yml`, `application-prod.yml`, ...)
- `src/main/docker` - Docker Compose files: the app, a local Fluentd (Doppler stand-in), SonarQube
- `src/test` - unit, integration (`*IT`), Karate, Cucumber and Gatling tests
- `bruno/` - API collection (auth, MBTA V3, observability, protobuf, route board)

## Development

The build requires **JDK 21** (the enforcer rejects other versions):

```bash
export JAVA_HOME=$(/usr/libexec/java_home -F -v 21)   # or: sdk env   (reads .sdkmanrc)
./mvnw                                               # dev profile on http://localhost:8081
```

Health: `http://localhost:8081/management/health`. With the `dev` profile, OpenAPI docs are at `/v3/api-docs`.

**Bruno**: open `bruno/` as a collection in [Bruno](https://www.usebruno.com), pick the `local` environment, run it.
Or from the command line (app running): `cd bruno && npx --yes @usebruno/cli@4.3.0 run --env local` (30 requests,
40 tests).

## Logging and metrics

| Profile | Sends | Needs |
|---|---|---|
| *(always)* | Prometheus metrics at `/management/prometheus` (also scraped by the CloudWatch-export Lambda) | - |
| `json-logs` (on in `prod`) | JSON console logs | - |
| `splunk` | Logs to Splunk HTTP Event Collector | `SPLUNK_HEC_URL`, `SPLUNK_HEC_TOKEN` (optional `SPLUNK_INDEX`, `SPLUNK_SOURCETYPE`) |
| `fluentd` | Logs to Fluentd / Doppler (forward protocol) | `FLUENTD_HOST` (optional `FLUENTD_PORT`, `FLUENTD_TAG`) |
| `dynatrace` | App metrics to the local Dynatrace OneAgent (OneAgent also traces) | - |
| `datadog` | Metrics to Datadog (optional, not in the target stack) | `DATADOG_API_KEY` |

```bash
SPRING_PROFILES_ACTIVE=prod,splunk,fluentd,dynatrace \
SPLUNK_HEC_URL=https://splunk.example.com:8088 SPLUNK_HEC_TOKEN=... FLUENTD_HOST=doppler.example.com \
java -jar target/voicebio-api-0.0.1-SNAPSHOT.jar
```

A profile without its required settings stops startup with a clear message. Set `DEPLOY_ENV` (`dev`, `staging`,
`prod`...) so metrics and logs carry the environment. Details:
[`docs/vendor-integrations.md`](docs/vendor-integrations.md).

`splunk-library-javalogging` comes from Splunk's Maven repository (declared in the pom); add it to your corporate
Maven proxy if you use one.

## Code formatting

Java code follows google-java-format, enforced by Spotless: the build fails on unformatted code.

```bash
./mvnw spotless:apply     # format before committing
```

## Testing

```bash
./mvnw verify      # unit + integration tests, incl. Karate API tests against mocked MBTA APIs (no network)
```

Karate against a running instance (start it with `./mvnw` first; uses the real MBTA APIs):

```bash
./mvnw test-compile failsafe:integration-test failsafe:verify -Dit.test=KarateLiveIT -Dkarate.baseUrl=http://localhost:8081
```

Load test (app running with `dev`):

```bash
./mvnw gatling:test -Dgatling.simulationClass=gatling.simulations.MbtaSimulation -Dusers=20 -Dramp=10
```

## Continuous integration

GitHub Actions ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)) runs on every push to `main` and every pull
request: `./mvnw -Pprod verify` on JDK 21 (formatting check, unit and integration tests, Karate against mocked MBTA
APIs), then builds the container image without pushing it. Test reports are uploaded as an artifact.

## Building for production

```bash
./mvnw -Pprod clean verify
java -jar target/*.jar
```

## Docker

Build an image with [Jib](https://github.com/GoogleContainerTools/jib) (no Dockerfile; base `eclipse-temurin:21-jre`,
non-root):

```bash
./mvnw -Pprod verify -DskipTests jib:dockerBuild
# on Apple Silicon / ARM:
./mvnw -Pprod verify -DskipTests jib:dockerBuild -Djib-maven-plugin.architecture=arm64
docker compose -f src/main/docker/app.yml up -d --wait
```

The container honours `APP_SLEEP` (seconds to wait before starting) and `JAVA_OPTS`.

Local log destination for the `fluentd` profile:

```bash
docker compose -f src/main/docker/fluentd.yml up -d
SPRING_PROFILES_ACTIVE=dev,fluentd FLUENTD_HOST=localhost ./mvnw
docker compose -f src/main/docker/fluentd.yml logs -f
```

## Code quality

```bash
docker compose -f src/main/docker/sonar.yml up -d
./mvnw -Pprod clean verify sonar:sonar -Dsonar.login=admin -Dsonar.password=admin
```

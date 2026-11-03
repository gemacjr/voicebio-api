---
title: Home
nav_order: 1
description: Overview of the Voicebio API and a map of this design guide.
permalink: /
---

# Voicebio API: Spring Boot design guide
{: .fs-9 }

How this service is built, why each decision was made, and how to apply the same practices elsewhere.
{: .fs-6 .fw-300 }

---

## What this service is

Voicebio API is a **stateless REST microservice** built on **Spring Boot 4** and **Java 21**. It sits behind a
gateway, authenticates callers with **JWT bearer tokens**, and serves Boston (MBTA) public transit data from two
public sources, chosen so the team can practise real-world integration:

| Feature | Endpoints | Upstream |
|---|---|---|
| REST client practice | `/api/mbta/routes`, `/routes/{id}`, `/routes/{id}/stations`, `/routes/{id}/alerts`, `/stops/{id}/predictions` | **MBTA V3 REST API** (JSON:API, rate limited) |
| Protocol Buffers | `/api/transit/vehicles`, `/vehicles/{id}` (protobuf **or** JSON) | **GTFS-Realtime** feed (binary protobuf) |
| Combined | `/api/routes/{routeId}/live` | **Both**, called in parallel and joined |

Every cross-cutting concern a real service needs is implemented on top, so the team can copy the patterns:

| Concern | How it is done here | Guide page |
|---|---|---|
| Layered architecture, enforced by tests | Controller → Service → Client, checked by ArchUnit | [Architecture](architecture.md) |
| Calling REST APIs | Declarative `@HttpExchange` interface on `RestClient`; JSON:API; rate limits | [HTTP clients](http-clients.md) |
| Protocol Buffers | Public protobuf feed in; protobuf or JSON out of one endpoint | [Protocol Buffers](protobuf.md) |
| Combining sources | Parallel calls on virtual threads, per-source timeouts, partial results | [Route board](route-board.md) |
| Configuration and profiles | `application*.yml`, profile groups, type-safe properties | [Configuration](configuration.md) |
| Errors | RFC 7807 Problem Details; 502/503 + `Retry-After` for upstream failures | [Error handling](error-handling.md) |
| Security | OAuth2 resource server, HS512 JWT, stateless | [Security](security.md) |
| Logging | Logback + `@Slf4j`, MDC request correlation across threads | [Logging](logging.md) |
| Metrics | Micrometer, Actuator, Prometheus | [Observability](observability.md) |
| Splunk HEC, Fluentd, Prometheus/CloudWatch, Dynatrace OneAgent | Logback appenders and exporters per profile | [Logging & metrics platforms](vendor-integrations.md) |
| Performance | Caching by data volatility, virtual threads, timeouts, Gatling | [Performance](performance.md) |
| Tests | Unit, integration, Karate API, architecture, BDD, load | [Testing](testing.md) |
| Build and containers | Maven on JDK 21, Jib images, Docker Compose | [Build and deploy](build-and-deploy.md) |

Also: a [Demo guide](demo-guide.md), a [Best-practice checklist](best-practices.md), and the
[JHipster migration notes](jhipster-migration.md).

## Architecture at a glance

```mermaid
flowchart LR
    caller([Caller / Gateway / Bruno]) -->|HTTPS + JWT| filter

    subgraph app [voicebio-api]
        direction LR
        filter[RequestIdFilter<br/>MDC requestId] --> sec[Spring Security<br/>JWT validation]
        sec --> ctrl[Controllers<br/>Mbta, Transit, RouteBoard]
        ctrl --> svc[Services<br/>cache, metrics, mapping]
        svc --> v3c[MbtaV3Client<br/>@HttpExchange, JSON:API]
        svc --> rtc[GtfsRealtimeClient<br/>@HttpExchange, protobuf]
        ctrl -. errors .-> advice[ExceptionTranslator<br/>RFC 7807]
    end

    v3c --> v3[(api-v3.mbta.com)]
    rtc --> rt[(cdn.mbta.com<br/>GTFS-Realtime)]
    app -->|logs| logs[(Splunk HEC,<br/>Fluentd)]
    app -->|/management/prometheus| prom[(Prometheus,<br/>CloudWatch Lambda)]
    oa[Dynatrace OneAgent] -.instruments.-> app
```

## Technology stack

| Area | Technology | Version |
|---|---|---|
| Language / runtime | Java (compiler `release`, enforcer and container image all on 21) | 21 (LTS) |
| Framework | Spring Boot / Spring Framework | 4.1.1 / 7.0.9 |
| Web | Spring MVC on embedded Tomcat, **virtual threads** | |
| Security | Spring Security OAuth2 Resource Server (Nimbus JOSE) | |
| HTTP client | `RestClient` + HTTP interface clients (`@HttpExchange`) | |
| JSON | Jackson 3 (`tools.jackson`) | |
| Protobuf | protobuf-java + protobuf-java-util, `protoc` via `io.github.ascopes:protobuf-maven-plugin` | 4.35.1 / 5.1.8 |
| Observability | Micrometer (Observation API), Actuator; Prometheus and Dynatrace registries; Dynatrace OneAgent for tracing | |
| Log shipping | Splunk HEC appender (`splunk-library-javalogging`), Fluentd appender (`logback-more-appenders` + Fluency) | 1.11.11 / 1.8.9 + 2.7.4 |
| Caching | Caffeine via JCache (JSR-107) | |
| API docs | springdoc-openapi | 3.1.1 |
| Boilerplate | Lombok (`@Slf4j`) | 1.18.46 |
| Tests | JUnit 5, Mockito, AssertJ, MockMvc, ArchUnit, Karate, Cucumber, Gatling | ArchUnit 1.5.0, Karate 1.5.2, Cucumber 7.34.8, Gatling 3.15.1 |
| Build | Maven (wrapper), Jib, Spotless, Checkstyle (nohttp), Modernizer, JaCoCo | Jib 3.5.2 |

## Quick start

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # the build requires JDK 21
./mvnw                      # run with the dev profile on http://localhost:8081
./mvnw verify               # unit + integration tests
```

Then get a token and call the API (the token endpoint exists **only** in the `dev` profile):

```bash
TOKEN=$(curl -s -X POST localhost:8081/api/authenticate -H 'Content-Type: application/json' -d '{}' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["id_token"])')

curl -s -H "Authorization: Bearer $TOKEN" localhost:8081/api/routes/Red/live
# {"route":{"id":"Red","name":"Red Line",...},"vehicles":[...],"sources":{"v3":"OK","gtfsRealtime":"OK"}}
```

Or open the `bruno/` folder in [Bruno](https://www.usebruno.com) and follow the [Demo guide](demo-guide.md).

## Project layout

```
voicebio-api/
├── bruno/                          API collection (auth, CRUD, observability) for Bruno
├── docs/                           this guide (GitHub Pages)
├── src/main/java/com/example/
│   ├── VoicebioApiApp.java         entry point, default profile, startup banner
│   ├── SpringProfiles.java         profile name constants (shared by all layers)
│   ├── aop/logging/                LoggingAspect: enter/exit/exception tracing in dev
│   ├── client/mbta/                MbtaV3Client (@HttpExchange, JSON:API) + upstream model + exceptions
│   ├── client/transit/             GtfsRealtimeClient (@HttpExchange, protobuf) + exception
│   ├── config/                     all @Configuration: security, cache, async, clients, docs...
│   ├── management/                 SecurityMetersService (JWT failure metrics)
│   ├── security/                   SecurityUtils, AuthoritiesConstants
│   ├── service/                    MbtaService, TransitService, RouteBoardService, CachedVehicleFeed, exceptions
│   │   └── dto/                    MbtaDtos, RouteBoardDtos: the API's public JSON contract
│   └── web/
│       ├── filter/                 RequestIdFilter (MDC correlation)
│       └── rest/                   MbtaController, TransitController, RouteBoardController, DevTokenController, errors/
├── src/main/proto/                 gtfs/gtfs-realtime.proto (vendored), voicebio/transit/v1/transit.proto (ours)
├── src/main/resources/config/      application.yml + per-profile overrides
├── src/main/docker/                Docker Compose: app, fluentd (local Doppler stand-in), sonar
└── src/test/
    ├── java/com/example/           unit tests (*Test) and integration tests (*IT)
    ├── java/gatling/simulations/   Gatling load test
    └── resources/                  test config, Karate features + mock, Cucumber features
```

## How to read this guide

Each page follows the same structure:

1. **What we do**: the design as it exists in the code, with file references.
2. **Why**: the reasoning, and the alternatives that were rejected.
3. **Best practice**: the general rule to apply in other services.
4. **Lessons learned**: real bugs found while building this, so they are not repeated.

{: .note }
Code snippets in this guide are taken from the repository. When the code changes, update the matching page in the
same pull request; the guide is only useful while it is true.

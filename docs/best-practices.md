---
title: Best-practice checklist
nav_order: 16
description: A consolidated Spring Boot design checklist to use in reviews, with links to the detailed pages.
---

# Spring Boot best-practice checklist

Use this page in design reviews and pull requests. Each item links to the page that explains it, with the
implementation in this repository as the example.
{: .fs-5 .fw-300 }

## Design and structure

- [ ] Code is layered (web → service → client), and **controllers never call external systems**. ([Architecture](architecture.md))
- [ ] Layer rules are **enforced by an ArchUnit test**, and a failure is treated as a design discussion. ([Architecture](architecture.md#the-rules-are-enforced-by-a-test))
- [ ] **Constructor injection** with `final` fields; no field `@Autowired`.
- [ ] Data is modelled with **records**; the public API DTO is separate from upstream models (anti-corruption layer). ([Architecture](architecture.md#two-models-on-purpose))
- [ ] Names that must match (cache names, client groups, profiles) are **constants**.
- [ ] Javadoc explains **why**, especially non-obvious constraints.

## Configuration

- [ ] **One artifact for all environments**; differences in profiles and environment variables. ([Configuration](configuration.md))
- [ ] Application settings use **`@ConfigurationProperties`** with `ignoreUnknownFields = false`.
- [ ] **Spring Boot's standard properties** are used wherever they exist.
- [ ] Time settings use **`Duration`** (`5s`, `10m`).
- [ ] **No real secrets in the repository**; sample secrets are clearly named and never used outside dev/CI. ([Configuration](configuration.md#secrets))

## HTTP clients

- [ ] New clients are **`@HttpExchange` interfaces** on `RestClient` (WebClient only in reactive apps). ([HTTP clients](http-clients.md))
- [ ] Every client has **connect and read timeouts**. ([HTTP clients](http-clients.md#timeouts-are-mandatory))
- [ ] Upstream errors are **mapped to exceptions in the client layer** and to **domain errors in the service**.
- [ ] Upstream failures return **502**, not 500.
- [ ] PATCH and partial payloads **omit null fields**. ([Lesson](http-clients.md#lesson-patch-and-null-fields))
- [ ] URLs use **templates** (`/routes/{id}`), never string concatenation.
- [ ] The client is designed for the API's **rate limit**: caching, a remaining-quota gauge, 503 + `Retry-After` when reached. ([HTTP clients](http-clients.md#rate-limiting-design-for-the-limit-you-have))

## Protocol Buffers

- [ ] Our `.proto` contract is **separate from upstream schemas** and versioned in its package (`v1`). ([Protocol Buffers](protobuf.md#two-schemas-two-roles))
- [ ] Field numbers are **never changed or reused**; removed fields are `reserved`.
- [ ] Code is **generated at build time** with the protoc version that matches the runtime; generated code is not committed.
- [ ] Vendored `.proto` files are **pinned to a commit** with their source recorded.
- [ ] Mapping respects **field presence** (`hasX()`); defaults are not data.
- [ ] Protobuf endpoints also offer **JSON** by content negotiation, and errors stay **problem+json**.

## Combining sources

- [ ] Independent upstream calls run **in parallel** on a **bounded** executor that propagates MDC. ([Route board](route-board.md))
- [ ] Each source has its own **time budget**; one failing source **degrades** the response instead of failing it, and the response **says what is missing**.
- [ ] Degradations are **counted** per source.

## Errors

- [ ] All errors are **RFC 7807 Problem Details** from one `@ControllerAdvice`. ([Error handling](error-handling.md))
- [ ] Domain exceptions carry **`@ResponseStatus`**; no HTTP types in services.
- [ ] Input is validated with **Bean Validation**, and field errors are returned.
- [ ] Internal details are **masked in production**; full details are logged server-side with the request id.
- [ ] Error **headers and bodies are tested**, not just status codes.

## Security

- [ ] **Stateless** resource server; sessions off; CSRF off only because no cookies are used. ([Security](security.md))
- [ ] **Deny by default**; specific rules before broad ones.
- [ ] Health probes public; **sensitive actuator endpoints require an admin role**.
- [ ] Prefer **asymmetric JWT signing (JWKS)** in production. ([Security](security.md#symmetric-vs-asymmetric-signing))
- [ ] Test-only helpers are **`@Profile`-gated** and **verified absent** from production artifacts.
- [ ] Client-supplied values that reach logs (headers) are **validated**.

## Logging

- [ ] SLF4J only; **`@Slf4j`** consistently. ([Logging](logging.md))
- [ ] **Placeholders**, not concatenation; **exception as the last argument**.
- [ ] INFO tells a request's story in a few lines; DEBUG for developer detail.
- [ ] Every request has a **correlation id in MDC**, returned to the caller. ([Logging](logging.md#correlating-a-request-mdc))
- [ ] MDC is **cleared in `finally`** and **propagated** to async threads.
- [ ] **JSON logs** in production, written to stdout for the platform's log shipper; **verified by looking at real output**. ([Vendor integrations](vendor-integrations.md#splunk-http-event-collector))
- [ ] **No secrets in logs**, including through `toString()`.

## Observability

- [ ] Services are instrumented with the **Observation API** (`@Observed`). ([Observability](observability.md))
- [ ] Key **business events have metrics**.
- [ ] Metric **tags have bounded cardinality**; no ids in tags. ([Observability](observability.md#automatic-http-metrics-and-cardinality))
- [ ] Timers publish **histograms**; percentiles are computed in Prometheus.
- [ ] New metrics are **checked in the Prometheus output**. ([Lesson](observability.md#lesson-prometheus-renamed-a-counter))
- [ ] **Liveness does not depend on downstream systems**.
- [ ] Vendor exporters are **off by default** and switched on **per environment by profile**; the code never names a vendor. ([Vendor integrations](vendor-integrations.md))
- [ ] A vendor profile without its credentials **fails startup**, instead of failing silently later.
- [ ] Log shipping is **asynchronous and bounded** (never blocks requests, never exhausts the heap). ([Logging & metrics platforms](vendor-integrations.md))
- [ ] Optional settings are **left unset**, not mapped to empty defaults (`${VAR:}`). ([Lesson](http-clients.md#lesson-an-empty-default-is-a-value))
- [ ] **One tracer per JVM** (here Dynatrace OneAgent).
- [ ] Metric names consumed by other systems (the CloudWatch Lambda) are treated as an **API**.
- [ ] **Trace ids are in every log line**, so logs and traces can be joined in any tool.

## Performance

- [ ] Optimization starts from **measurements** (metrics, load tests). ([Performance](performance.md))
- [ ] Caches are **bounded** (size and TTL), **evicted on write**, and their **hit ratio is monitored**.
- [ ] **Virtual threads** for I/O-bound MVC services.
- [ ] Load tests have **assertions** and use **stubs for third parties**.

## Testing

- [ ] Business logic is **unit-tested without Spring**. ([Testing](testing.md))
- [ ] The web layer is **integration-tested with MockMvc** (validation, security, errors).
- [ ] HTTP clients are tested against **`MockRestServiceServer`**, asserting the **exact request**.
- [ ] Each mocked boundary is **verified once against the real system**, and findings become regression tests. ([Testing](testing.md#lessons-what-each-layer-caught))
- [ ] API behaviour is covered by **Karate** features that run **self-contained in CI** (mocked upstream) and **live** against any environment. ([Testing](testing.md#karate-api-tests))
- [ ] **Documented commands are run exactly as documented** before they are published. ([Lesson](build-and-deploy.md#lesson-mvnw-never-worked))

## Build and deploy

- [ ] Builds use the **Maven wrapper**; JDK and Maven versions are enforced. ([Build and deploy](build-and-deploy.md))
- [ ] Compile-time-only dependencies (Lombok) are **excluded from the jar**. ([Lesson](build-and-deploy.md#lesson-provided-does-not-keep-lombok-out-of-a-boot-jar))
- [ ] Images are built with **Jib**: JRE-only base, **non-root**, layered.
- [ ] The app is **PID 1** (`exec java`) for graceful shutdown.
- [ ] Local compose files bind ports to **127.0.0.1**.
- [ ] **What is shipped is inspected**: jar contents, image config.

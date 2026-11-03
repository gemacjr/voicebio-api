---
title: JHipster migration
nav_order: 16
description: What was removed from the generated JHipster project, what replaced it, and the behaviour changes.
---

# JHipster migration notes
{: .no_toc }

The project was generated with JHipster 9.4.0 (microservice, JWT, no client), then turned into a plain Spring Boot
application. This page records what changed and why, so nobody has to rediscover it.
{: .fs-5 .fw-300 }

<details open markdown="block">
  <summary>Contents</summary>
  {: .text-delta }
1. TOC
{:toc}
</details>

## Why remove JHipster

JHipster is two things, and both were removed:

1. **A code generator** (Node.js, Yeoman). It needs `package.json`, `node_modules`, `.yo-rc.json`, Husky and
   lint-staged hooks, Prettier for Java, and `jhipster-needle-*` comments as insertion points for later generation.
   We do not regenerate, so all of that was dead weight and an extra toolchain for a Java team.
2. **A runtime library** (`tech.jhipster:jhipster-framework`), which the generated Java code imported. It hid behaviour
   behind auto-configuration, and every Spring Boot upgrade had to wait for a matching JHipster release.

The result: a standard Spring Boot project that any Spring developer can read, upgrade on Boot's schedule, and build
with only a JDK.

## Removed

| Item | Was |
|---|---|
| `package.json`, `package-lock.json`, `node_modules/` | Node toolchain for the generator, Prettier, Husky |
| `.yo-rc.json` | Generator configuration (it also contained a copy of the JWT secret) |
| `.husky/`, `.lintstagedrc.cjs`, `.prettierrc`, `.prettierignore` | Git hooks and formatting through Node |
| `GeneratedByJHipster.java` | Marker annotation |
| `src/main/docker/jhipster-control-center.yml` | JHipster admin UI |
| `tech.jhipster:jhipster-framework` | Runtime library (no transitive dependencies were lost: all of its own dependencies were optional) |
| Spring Cloud OpenFeign, Resilience4j circuit breaker | Unused generated client infrastructure (`FeignConfiguration`, `UserFeignClientInterceptor`) |
| `jhimetrics`, `jhiopenapigroups` actuator endpoints | JHipster-specific; Prometheus and springdoc groups replace them |
| `zipkin` Maven profile, `zipkin.yml` (Brave tracer) | Broken in Boot 4; tracing is now done by Dynatrace OneAgent |
| `docker-compose` Maven profile, `spring.docker.compose.*` | Pointed at a non-existent `no.yml`; broke `./mvnw` |

## Replaced

| JHipster class | Replacement |
|---|---|
| `JHipsterProperties` | `config/ApplicationProperties` (`application.*`: CORS, cache, JWT, API docs) |
| `JHipsterConstants`, `DefaultProfileUtil` | `com.example.SpringProfiles`, `VoicebioApiApp.addDefaultProfile()` |
| `ProblemDetailWithCause` (+ builder) | `web/rest/errors/ProblemDetailWithCause` |
| `PrefixedKeyGenerator` | `config/PrefixedKeyGenerator`, built on Spring's `SimpleKey` |
| `HeaderUtil.createFailureAlert` | Private method in `ExceptionTranslator` |
| `ExceptionHandlingAsyncTaskExecutor` | Removed: plain `ThreadPoolTaskExecutor` + `SimpleAsyncUncaughtExceptionHandler` (+ `MdcTaskDecorator`) |
| `LoggingUtils` / `LoggingConfiguration` (Logstash) | Spring Boot structured logging (`logging.structured.format.console`), no extra dependency |
| `JHipsterSpringDocAutoConfiguration` | `config/SpringDocConfiguration`: `OpenAPI` info + `api`/`management` groups |
| `LoggerFactory.getLogger(...)` fields | Lombok `@Slf4j` |

{: .note }
JHipster's JSON logging needed `logstash-logback-encoder`, which was **not** in the pom: enabling it would have
crashed the application. The Spring Boot replacement works out of the box.

## Behaviour changes to be aware of

| Change | Impact |
|---|---|
| Config namespace `jhipster.*` → `application.*` | Environment variables change, e.g. `JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET` → `APPLICATION_SECURITY_JWT_BASE64_SECRET` |
| JWT token-validity settings removed | This service only validates tokens; it never issued them |
| Problem `type` URIs `https://www.jhipster.tech/problem/...` → `urn:problem-type:voicebio:...` | Only matters to clients matching on `type` |
| Container variable `JHIPSTER_SLEEP` → `APP_SLEEP` | Update deployment manifests |
| `jhimetrics`, `jhiopenapigroups` endpoints gone | Use `/management/metrics`, `/management/prometheus`, `/v3/api-docs/{group}` |
| Java no longer auto-formatted on commit | Add a Spotless formatter if wanted (see [Build and deploy](build-and-deploy.md#quality-gates-in-the-build)) |

## Generated bugs fixed along the way

| Bug | Fix | Details |
|---|---|---|
| `BadRequestAlertException` alert headers never sent | Headers added in `handleExceptionInternal` | [Error handling](error-handling.md#lesson-the-headers-were-never-sent) |
| Console log pattern dropped all MDC values (including trace ids) | `%X{requestId}` and `${LOG_CORRELATION_PATTERN}` added | [Logging](logging.md#the-log-pattern) |
| Prometheus scrape path was the gateway path `/services/voicebioApi/management/prometheus` | Corrected (the local Prometheus stack was later removed) | [Observability](observability.md) |
| `app.yml` published no port | `127.0.0.1:8081:8081` added | [Build and deploy](build-and-deploy.md#running-with-docker-compose) |
| `./mvnw` failed: `src/main/docker/no.yml must exist` | Docker Compose integration removed | [Build and deploy](build-and-deploy.md#lesson-mvnw-never-worked) |
| JSON logging could not be switched on (custom logback config used the text appender) | `structured-console-appender.xml` under the `json-logs` profile | [Vendor integrations](logging.md#json-logs-for-production) |
| Logback startup warnings on stdout (`converterClass`, `scan`) | Fixed; stdout is pure JSON in prod | [Vendor integrations](logging.md#json-logs-for-production) |
| `prod` sampled 100% of traces | Default 10% | [Vendor integrations](observability.md#tracing-dynatrace-oneagent) |

## Verifying a migration like this

The approach used, which applies to any large refactor:

1. **Baseline first**: run the full build before changing anything (here: 31 unit and 17 integration tests green).
2. **Measure the dependency**: list every import of the library before deciding how to replace it.
3. **Replace, then compare**: the same test counts must pass afterwards, plus a real application start.
4. **Search for leftovers**: `grep -ri jhipster` across sources, config, Docker and IDE files.
5. **Check the artifacts**, not just the tests: run the prod jar, inspect the jar and image contents.

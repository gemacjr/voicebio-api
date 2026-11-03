---
title: Performance
nav_order: 12
description: Caching by data volatility, virtual threads on Java 21, parallel upstream calls, timeouts, rate limits and load testing with Gatling.
---

# Performance
{: .no_toc }

<details open markdown="block">
  <summary>Contents</summary>
  {: .text-delta }
1. TOC
{:toc}
</details>

## Where the time goes

Almost all request time is **waiting on MBTA**. Measured on a developer Mac:

| Path | Typical latency |
|---|---|
| MBTA V3 call (`/routes/{id}`, `/predictions`) | 20–150 ms |
| MBTA V3 stations with platforms (`include=child_stops`, ~460 KB) | ~150 ms |
| GTFS-Realtime feed download (~90 KB protobuf) | 50–250 ms |
| Route board, cold (both sources, in parallel) | ~340 ms |
| Any request served from cache | **single-digit ms** |

So the levers are: **don't make the call** (caching), **make independent calls at the same time** (parallelism),
**don't waste threads while waiting** (virtual threads), and **don't wait forever** (timeouts).

## Caching by data volatility

| Cache | Holds | Lifetime | Max entries |
|---|---|---|---|
| `mbtaRoutes` | Routes (list and single) | 1 h (`application.cache.caffeine`) | 100 dev / 1000 prod |
| `mbtaStations` | Stations with platforms, per route | 1 h | same |
| `mbtaPredictions` | Arrival predictions, per stop/route/limit | 15 s (`application.cache.realtime-time-to-live-seconds`) | 500 |
| `mbtaAlerts` | Service alerts, per route | 15 s | 100 |
| `transitFeed` | The parsed GTFS-Realtime feed | 15 s | 1 |

The lifetimes follow how often the data changes: route names change a few times a year, vehicle positions every
10–15 seconds. One `CacheConfiguration` creates every cache from two settings, and cache names are constants shared
by `@Cacheable` and the configuration.

```java
@Cacheable(cacheNames = STATIONS_CACHE, key = "#routeId")
public List<StationDTO> stations(String routeId) { ... }
```

Measured during a live session: 25 Karate scenarios and 90 Gatling requests needed **11 MBTA V3 calls and 1 feed
download**.

{: .warning }
Each instance has its own Caffeine cache, so N instances make N times the upstream calls (and share MBTA's rate limit
if they share an IP). For many instances, consider a shared cache (Redis) or an MBTA API key per environment.

### Caching checklist

* Cache **expensive, repeated reads** of data that tolerates some staleness; size the lifetime to the data.
* **Bound** size and lifetime; never cache without both.
* Use **explicit, stable keys** (`key = "#stopId + ':' + #routeId + ':' + #limit"`).
* Enable **statistics** and watch hit ratios (`cache.gets`).
* Cache **immutable** values (records, protobuf messages): callers share instances.
* `@Cacheable` works through a proxy: put cached methods on a **different bean** from their callers
  (`CachedVehicleFeed`).

## Parallel upstream calls

The route board needs two independent sources. Sequentially it would cost their **sum**; in parallel, the **slower**:

```java
CompletableFuture<ReferenceData> reference = async(() -> new ReferenceData(mbta.route(routeId), mbta.stations(routeId)));
CompletableFuture<VehicleList> positions = async(() -> transit.vehicles(routeId, ALL_VEHICLES));
```

`RouteBoardServiceTest.callsRunInParallel` asserts it: two 300 ms sources answer in under 550 ms. See
[Route board](route-board.md).

## Virtual threads on Java 21

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

Tomcat handles each request on a **virtual thread** (`tomcat-handler-N` in logs), and the route board's parallel calls
run on a bounded virtual-thread executor (`upstream-N`). A blocked virtual thread parks cheaply instead of holding an
OS thread, so concurrency for I/O-bound work is limited by memory, not a thread pool.

| | Platform threads | Virtual threads |
|---|---|---|
| Cost per thread | ~1 MB stack, OS-scheduled | A few KB, JVM-scheduled |
| Good for | CPU-bound work | **Waiting on I/O** (HTTP, DB) |
| Code style | Blocking | Same blocking code |

{: .warning }
**Pinning on Java 21.** On JDK 21, a virtual thread that blocks inside a `synchronized` block or method **pins** its
carrier (OS) thread, which can exhaust the small carrier pool under load. (JDK 24 removed this limitation; this
project runs on 21.) In code on the request path, prefer `java.util.concurrent` locks (`ReentrantLock`) to
`synchronized` around blocking calls. To find pinning, run with `-Djdk.tracePinnedThreads=short` or watch the JFR event
`jdk.VirtualThreadPinned`.

Other caveats:

* Virtual threads make **downstream** limits matter more. The upstream executor has a **concurrency limit (50)** so a
  traffic spike cannot fire hundreds of parallel calls at MBTA.
* `ThreadLocal` and MDC work (each virtual thread has its own), but do not cache expensive objects in `ThreadLocal`:
  virtual threads are not pooled.

## Timeouts at two levels

| Level | Setting | Purpose |
|---|---|---|
| HTTP client | `spring.http.serviceclient.<group>.connect-timeout` / `read-timeout` (5 s / 10 s) | Never hang on a dead connection |
| Per source in the route board | `orTimeout(4 s)` | Bound one request's total time; return a partial board instead |

Keep your own timeouts **below your callers' timeouts**, or they give up before you do.

## Rate limits are a capacity limit

MBTA V3 allows **20 requests/minute** without a key. That is a hard capacity ceiling for cache misses, so it shapes the
design: caching first, a single call for stations + platforms (`include`), the `mbta.ratelimit.remaining` gauge, and a
clean **503 + `Retry-After`** when it is reached. An `MBTA_API_KEY` raises it to 1,000/min.

## Load testing with Gatling

`src/test/java/gatling/simulations/MbtaSimulation.java`: users authenticate, list subway routes, then three times pick
a random line (Red, Orange, Blue) and fetch the route and its live board, and finally request the transit vehicles as
protobuf:

```bash
./mvnw                                    # terminal 1, dev profile
./mvnw gatling:test -Dgatling.simulationClass=gatling.simulations.MbtaSimulation -Dusers=20 -Dramp=10
```

### Measured result (10 users over 10 seconds, live MBTA)

| Request | Count | Failures |
|---|---|---|
| Authenticate | 10 | 0 |
| Subway routes | 10 | 0 |
| Route | 30 | 0 |
| Route board | 30 | 0 |
| Transit vehicles (protobuf) | 10 | 0 |
| **All** | **90** | **0** |

| Statistic | Value |
|---|---|
| Mean response time | 11 ms |
| p50 / p95 / p99 | 8 / 14 / 151 ms |
| Assertion: failed requests < 1% | ✅ passed (0%) |

The p99 is the cold path (first board per line: two upstream sources); everything else is served from cache.

{: .warning }
MBTA's APIs are public services. Keep load tests small, rely on caching, and for real capacity tests point
`spring.http.serviceclient.mbta.base-url` and `.transit.base-url` at stubs (the Karate mocks can be started standalone).

## Best practices

* **Measure first** (here: the network dominates).
* **Cache by data volatility**, bounded, with statistics.
* **Call independent dependencies in parallel**, each with its own time budget.
* **Virtual threads** for I/O-bound MVC services; avoid `synchronized` around blocking calls on JDK 21.
* **Bound concurrency towards dependencies**, and design for their rate limits.
* **Every outbound call has a timeout.**
* **Load test** with assertions, against stubs for third parties.

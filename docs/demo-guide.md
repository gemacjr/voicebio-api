---
title: Demo guide
nav_order: 15
description: A step-by-step script for demonstrating the REST and protobuf clients, the combined route board, logging, metrics and tests.
---

# Demo guide
{: .no_toc }

A 25-minute walkthrough that shows every concern in this guide working against live MBTA data.
{: .fs-5 .fw-300 }

<details open markdown="block">
  <summary>Contents</summary>
  {: .text-delta }
1. TOC
{:toc}
</details>

## Before the demo

| Need | Check |
|---|---|
| JDK **21** | `java -version`, or `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` |
| [Bruno](https://www.usebruno.com) desktop app (or CLI `bru`) | installed |
| Internet access | the app calls `api-v3.mbta.com` and `cdn.mbta.com` |
| Docker (optional) | for the Fluentd step |
| Port 8081 free | nothing else running on it |
| Optional `MBTA_API_KEY` | free key from MBTA; raises their limit from 20 to 1,000 requests/minute |

Start the app and **keep the console visible**:

```bash
./mvnw
```

In Bruno: **Open Collection** → `bruno/` → environment **local**.

## The collection

| Folder | Requests | Shows |
|---|---|---|
| **1 Auth** | Get token · Request without token | JWT security; the token is reused by every request |
| **2 MBTA V3 (REST)** | Subway routes · Red Line · Unknown route · Invalid id · Stations · Arrivals · Alerts · Invalid limit | REST client on a JSON:API, caching, validation, RFC 7807 errors |
| **4 Transit (protobuf)** | JSON · protobuf · Red line · 404 · invalid limit | Protobuf feed in, protobuf or JSON out |
| **5 Route board (combined)** | Red Line live · Orange Line live · Unknown route | REST + protobuf in parallel, joined, partial failure |
| **6 Observability** | Health · Metrics · Service timer · Inbound/Outbound HTTP · Cache hits · Rate limit · Prometheus · Log levels · Correlated request | Micrometer, Actuator, MDC |

{: .tip }
Run the whole collection first (**Run** on the collection) to show everything green, then go request by request.

## Script

### 1. Security (2 min)

1. **1 Auth / Request without token** → 401.
2. **1 Auth / Get token** → 200. "Dev-only endpoint; it does not exist in prod. Tokens come from the gateway."
3. Point at `WARN ... Issued DEV-ONLY token` and `idToken=***` in the console: "we never log secrets."

### 2. REST client on a real API (4 min)

1. **Subway routes**: JSON:API reshaped into flat JSON. Console: `MBTA V3 GET https://api-v3.mbta.com/routes?filter%5Btype%5D=0%2C1 -> 200`.
   Run it again: **no** new MBTA line (cached for an hour).
2. **Red Line stations**: one MBTA call returns 22 stations *and* their platforms (`include=child_stops`).
3. **Next arrivals at Park Street**: realtime predictions, cached 15 s.
4. **Unknown route (404)** and **Invalid route id (400)**: MBTA's 404 becomes our RFC 7807 problem; an invalid id
   never reaches MBTA.
5. **6 Observability / MBTA rate limit remaining**: "20 requests/minute without a key; caching keeps us far below."

See [HTTP clients](http-clients.md).

### 3. Protocol Buffers (3 min)

1. **4 Transit / Vehicles as JSON**, then **Vehicles as protobuf**: same URL, only `Accept` changed; compare sizes
   (about 3× smaller).
2. **Unknown vehicle (404)**: errors stay readable `problem+json` even when protobuf was asked for.

See [Protocol Buffers](protobuf.md).

### 4. The combined route board (4 min)

1. **5 Route board / Red Line live**: positions from protobuf, station names and destinations from REST. Point at
   `sources: {v3: OK, gtfsRealtime: OK}`.
2. In the console, find the request's lines: the two upstream calls run **at the same time** on `upstream-1` and
   `upstream-2`, both tagged with the request id.
3. Explain partial failure: if the feed were down, the board would still return the route with
   `gtfsRealtime: UNAVAILABLE` (covered by `RouteBoardServiceTest`).

See [Route board](route-board.md).

### 5. Metrics (3 min)

1. **List metrics**: `mbta.service`, `route.board`, `transit.feed.age`, `mbta.ratelimit.remaining`.
2. **Service timer - route**: one `@Observed` annotation times every service method.
3. **Outbound calls to MBTA V3**: URI **templates** (`/routes/{id}`), never ids.
4. **Prometheus scrape**: "this is the contract the CloudWatch Lambda reads; names are an API."

See [Observability](observability.md).

### 6. Logging to Doppler/Fluentd (3 min, optional, needs Docker)

```bash
docker compose -f src/main/docker/fluentd.yml up -d
# restart the app with the fluentd profile
SPRING_PROFILES_ACTIVE=dev,fluentd FLUENTD_HOST=localhost ./mvnw
docker compose -f src/main/docker/fluentd.yml logs -f
```

Send **6 Observability / Correlated request (X-Request-Id)**: Fluentd prints JSON records with
`"requestId":"bruno-demo-1"`, including the ones from the `upstream-*` virtual threads. Splunk HEC works the same way
with the `splunk` profile. See [Logging & metrics platforms](vendor-integrations.md).

### 7. Log levels at runtime (1 min)

**Set log level to WARN**, re-run **Red Line live**: the INFO lines disappear. **Reset log level**: back. No restart.

### 8. Automated API tests with Karate (2 min, optional)

```bash
./mvnw test-compile failsafe:integration-test failsafe:verify -Dit.test=KarateLiveIT -Dkarate.baseUrl=http://localhost:8081
```

Open `target/karate-reports/live/karate-summary.html`: 25 scenarios readable as specifications. The same features run
in CI against Karate **mocks** of both MBTA APIs (`KarateApiIT`), offline.

### 9. Load test (optional, 2 min)

```bash
./mvnw gatling:test -Dgatling.simulationClass=gatling.simulations.MbtaSimulation -Dusers=10 -Dramp=10
```

90 requests, 0 failures, p95 about 14 ms, while MBTA sees only a handful of calls.

## Against the Docker container

The container runs the **prod** profile (no `/api/authenticate`, JSON logs). Sign a token with the sample secret, the
way a gateway would (Python 3, standard library only):

```bash
TOKEN=$(python3 - <<'EOF'
import base64, hashlib, hmac, json, re, time
yml = open("src/main/resources/config/application-secret-samples.yml").read()
secret = base64.b64decode(re.search(r"base64-secret:\s*(\S+)", yml).group(1))
b64 = lambda b: base64.urlsafe_b64encode(b).rstrip(b"=").decode()
now = int(time.time())
h = b64(json.dumps({"alg": "HS512"}).encode())
c = b64(json.dumps({"sub": "docker-demo", "auth": ["ROLE_USER", "ROLE_ADMIN"], "iat": now, "exp": now + 3600}).encode())
print(f"{h}.{c}.{b64(hmac.new(secret, f'{h}.{c}'.encode(), hashlib.sha512).digest())}")
EOF
)
curl -s -H "Authorization: Bearer $TOKEN" localhost:8081/api/routes/Blue/live
```

In Bruno, set the `token` variable in the **local** environment and skip *Get token*.

{: .warning }
This only works because the container uses the public sample secret, and it shows the weakness of shared-secret JWTs:
anyone with the secret can mint admin tokens. See [Security](security.md#symmetric-vs-asymmetric-signing).

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Every request 401 | Token missing or expired (1 h) | Re-run *1 Auth / Get token* |
| *Get token* returns 404 | App runs a non-dev profile | Use `./mvnw`, or sign a token (above) |
| 503 `MBTA API rate limited` + `Retry-After` | Over 20 MBTA calls/minute (cold caches, many restarts) | Wait `Retry-After` seconds, or set `MBTA_API_KEY` |
| 502 on MBTA endpoints | No internet, or MBTA down | `curl https://api-v3.mbta.com/routes/Red` |
| Route board `gtfsRealtime: UNAVAILABLE` | GTFS feed unreachable or slower than 4 s | Check `curl -I https://cdn.mbta.com/realtime/VehiclePositions.pb` |
| Enforcer error "builds and runs on JDK 21" | Wrong JDK | `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` |
| Startup fails "profile 'fluentd' requires FLUENTD_HOST" | Profile on, setting missing | Set the variable, or drop the profile |
| ERROR stack trace on a 404 in the console | `LoggingAspect` logs every exception in dev | Expected in dev |

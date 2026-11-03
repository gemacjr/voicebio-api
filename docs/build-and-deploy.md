---
title: Build and deploy
nav_order: 14
description: Maven build and profiles, Lombok setup, quality plugins, Jib container images and Docker Compose.
---

# Build and deploy
{: .no_toc }

<details open markdown="block">
  <summary>Contents</summary>
  {: .text-delta }
1. TOC
{:toc}
</details>

## Maven, always through the wrapper

```bash
./mvnw ...       # macOS / Linux
mvnw.cmd ...     # Windows
```

The wrapper (`.mvn/wrapper/`) pins the Maven version, so every developer and CI agent builds the same way. The
enforcer plugin also requires **Maven ≥ 3.2.5** and **JDK 21**, and fails fast otherwise:

```
This project builds and runs on JDK 21 (LTS). Set JAVA_HOME to a JDK 21.
```

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)     # macOS
sdk env                                               # SDKMAN reads .sdkmanrc (java=21)
```

The compiler uses `<release>21</release>` rather than `source`/`target`: it also rejects JDK APIs newer than 21, so code
that compiles is guaranteed to run on the Java 21 container. `.java-version` and `.sdkmanrc` let jenv and SDKMAN pick
the right JDK automatically.

### Common commands

| Goal | Command |
|---|---|
| Run locally (dev) | `./mvnw` (default goal `spring-boot:run`) |
| Unit + integration tests | `./mvnw verify` |
| Production jar | `./mvnw -Pprod clean verify` → `target/voicebio-api-0.0.1-SNAPSHOT.jar` |
| Production WAR | `./mvnw -Pprod,war clean verify` |
| Docker image (local daemon) | `./mvnw -Pprod verify -DskipTests jib:dockerBuild` |
| Docker image (Apple Silicon) | add `-Djib-maven-plugin.architecture=arm64` |
| Image as a tarball (no daemon) | `./mvnw -Pprod verify -DskipTests jib:buildTar` |
| Load test | `./mvnw gatling:test -Dgatling.simulationClass=gatling.simulations.MbtaSimulation` |
| Sonar analysis | `./mvnw -Pprod clean verify sonar:sonar` |

### Maven profiles

| Profile | Effect |
|---|---|
| `dev` (default) | Spring profile `dev`; adds `spring-boot-devtools` (auto-restart) |
| `prod` | Spring profile `prod`; adds `git-commit-id` (git info in `/management/info`), cleans `target/classes/static` |
| `api-docs` | Adds the `api-docs` Spring profile to a prod build |
| `tls` | Adds the `tls` Spring profile (HTTPS) |
| `war` | Packages a WAR for external servlet containers (`ApplicationWebXml`) |
| `IDE` | Puts the MapStruct processor on the IDE classpath |

## Annotation processors and Lombok

Three annotation processors run at compile time, **in this order**:

```xml
<annotationProcessorPaths>
    <path><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId></path>
    <path><groupId>org.springframework.boot</groupId><artifactId>spring-boot-configuration-processor</artifactId></path>
    <path><groupId>org.mapstruct</groupId><artifactId>mapstruct-processor</artifactId>...</path>
</annotationProcessorPaths>
```

Lombok goes first so the other processors see the code it generates. `spring-boot-configuration-processor` generates
metadata for `ApplicationProperties`, which drives IDE auto-completion in YAML.

### Dependencies from Splunk's repository

`splunk-library-javalogging` (the Splunk HEC appender) is not on Maven Central. The pom declares Splunk's repository,
**releases only**:

```xml
<repository>
    <id>splunk-artifactory</id>
    <url>https://splunk.jfrog.io/splunk/ext-releases-local</url>
    <releases><enabled>true</enabled></releases>
    <snapshots><enabled>false</enabled></snapshots>
</repository>
```

Maven Central is still consulted first. Behind a corporate proxy (Artifactory, Nexus), add this repository there.

### Protocol Buffers code generation

`io.github.ascopes:protobuf-maven-plugin` (version managed by Spring Boot) runs `protoc` in the `generate-sources`
phase on everything in `src/main/proto`. The gRPC generator that Spring Boot's parent adds is switched off
(`<plugins combine.self="override" />`) because this service uses plain protobuf messages. Generated packages
(`com.google.transit.realtime`, `com.example.api`) are excluded from Modernizer and from Sonar coverage. Details:
[Protocol Buffers](protobuf.md#code-generation).

### Lesson: `provided` does not keep Lombok out of a Boot jar
{: .text-purple-200 }

Lombok is a compile-time tool and is declared with `<scope>provided</scope>`. Inspecting the production jar still
found `BOOT-INF/lib/lombok-1.18.46.jar` (2 MB): **the Spring Boot Maven plugin packages `provided` dependencies by
design** (some WAR deployments need them). The fix is an explicit exclude:

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <configuration>
        <excludes>
            <exclude>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
            </exclude>
        </excludes>
    </configuration>
</plugin>
```

{: .lesson }
Inspect what you ship: `unzip -l target/*.jar | grep BOOT-INF/lib` takes a second and shows exactly what is in
production.

`lombok.config` at the project root sets `lombok.addLombokGeneratedAnnotation = true`, so JaCoCo and Sonar exclude
generated code from coverage, and `config.stopBubbling = true`, so settings from parent folders are ignored.

## Quality gates in the build

| Plugin | Phase | What it checks |
|---|---|---|
| `maven-enforcer-plugin` | validate | Maven and JDK versions; reports dependency convergence |
| `maven-checkstyle-plugin` + `nohttp` | verify | No plain `http://` URLs in `pom.xml` and `README.md` (`checkstyle.xml`) |
| `spotless-maven-plugin` | process-sources | Formatting hook (no formatter configured yet; see below) |
| `maven-surefire-plugin` | test | Unit tests (`*Test`) |
| `maven-failsafe-plugin` | integration-test, verify | Integration tests (`*IT`), run against the packaged classes |
| `jacoco-maven-plugin` | test, post-integration-test | Coverage reports for unit and integration tests |
| `modernizer-maven-plugin` | package | Flags legacy APIs that have modern JDK replacements |
| `sonar-maven-plugin` | on demand | Static analysis (settings in `sonar-project.properties`) |

{: .note }
Java formatting used to be done by Prettier through the removed Node tooling. To enforce formatting again, add a
formatter to Spotless, for example `<java><palantirJavaFormat/></java>`, and run `./mvnw spotless:apply` once.

## Container images with Jib

There is **no Dockerfile**. [Jib](https://github.com/GoogleContainerTools/jib) builds the image straight from the
Maven project:

```xml
<from><image>eclipse-temurin:21-jre-noble</image></from>   <!-- JRE only, no JDK or build tools -->
<to><image>voicebioapi:latest</image></to>
<container>
    <entrypoint><shell>bash</shell><option>-c</option><arg>/entrypoint.sh</arg></entrypoint>
    <ports><port>8081</port></ports>
    <environment>
        <SPRING_OUTPUT_ANSI_ENABLED>ALWAYS</SPRING_OUTPUT_ANSI_ENABLED>
        <APP_SLEEP>0</APP_SLEEP>
    </environment>
    <user>1000</user>                                         <!-- not root -->
</container>
```

Why Jib:

* **Layered**: dependencies, resources and classes are separate image layers. A code change rebuilds and pushes only
  the small classes layer; the ~60 MB of libraries are reused.
* **Reproducible**: no `Dockerfile` drift, and the same input produces the same layers.
* **No Docker needed to build**: `jib:build` pushes straight to a registry, and `jib:buildTar` writes a tarball.
  CI agents do not need a Docker daemon.
* **Secure defaults**: JRE-only base image, non-root user `1000`.

Verified properties of the built image (arm64): base `eclipse-temurin:21-jre-noble` (the container reports `openjdk 21.0.13 LTS`), user `1000`, port `8081/tcp`,
`APP_SLEEP=0`, `/entrypoint.sh` executable (`755`), no Lombok or Feign in any layer.

### The entrypoint

`src/main/docker/jib/entrypoint.sh`:

* waits `APP_SLEEP` seconds before starting (useful when a dependency container starts slowly)
* supports `*_FILE` secrets: `file_env 'SPRING_DATASOURCE_PASSWORD'` reads `SPRING_DATASOURCE_PASSWORD_FILE` if set
* starts the app with `exec java ${JAVA_OPTS} ... "com.example.VoicebioApiApp"`, so Java is PID 1 and receives
  `SIGTERM` directly: graceful shutdown (`server.shutdown: graceful` in prod) works

## Running with Docker Compose

```bash
./mvnw -Pprod verify -DskipTests jib:dockerBuild -Djib-maven-plugin.architecture=arm64
docker compose -f src/main/docker/app.yml up -d --wait
docker compose -f src/main/docker/app.yml down
```

`src/main/docker/app.yml`:

```yaml
services:
  app:
    image: voicebioapi
    environment:
      - _JAVA_OPTIONS=-Xmx512m -Xms256m
      - SPRING_PROFILES_ACTIVE=prod,api-docs,secret-samples
      - MANAGEMENT_PROMETHEUS_METRICS_EXPORT_ENABLED=true
    ports:
      - 127.0.0.1:8081:8081          # localhost only, not the whole network
    healthcheck:                      # uses /management/health, so --wait means "really ready"
      ...
```

Verified run: the container became **healthy** in about 5 seconds and passed all 24 Bruno checks. Because it runs the
`prod` profile, there is no `/api/authenticate`; tokens must come from a real issuer (or be signed with the sample
secret for local testing, see [Demo guide](demo-guide.md#against-the-docker-container)).

Other compose files:

| File | Starts |
|---|---|
| `fluentd.yml` | A local Fluentd printing every record: stand-in for Doppler (see [Logging & metrics platforms](vendor-integrations.md#fluentd-doppler)) |
| `sonar.yml` | SonarQube for local static analysis |

{: .warning }
`app.yml` uses `secret-samples`, so the JWT secret is the public sample one. It is for local use only. A real
deployment supplies `APPLICATION_SECURITY_JWT_BASE64_SECRET` from a secret store and does not activate
`secret-samples`.

## Production readiness checklist

| Item | Status here |
|---|---|
| Single artifact, configured per environment | ✅ Profiles + environment variables |
| Non-root container, JRE-only base | ✅ Jib config |
| Health, liveness, readiness probes | ✅ `/management/health/{liveness,readiness}` |
| Graceful shutdown | ✅ `server.shutdown: graceful` (prod) + `exec java` |
| Response compression | ✅ `server.compression` (prod) |
| Metrics export | ✅ Prometheus (enable per environment) |
| Structured logs | ✅ JSON on stdout in `prod` (`json-logs` profile) |
| Log and metric platforms | ✅ `splunk`, `fluentd`, `dynatrace` (OneAgent) profiles; Prometheus for the CloudWatch Lambda; fail fast on missing settings |
| Real secret management | ⚠️ To do per environment |
| Pinned base image digest | ⚠️ Jib warns the tag is not pinned; pin `image@sha256:...` for reproducible releases |
| Memory limits | ⚙️ `_JAVA_OPTIONS` in compose; in Kubernetes prefer `-XX:MaxRAMPercentage` with container limits |

### Lesson: `./mvnw` never worked
{: .text-purple-200 }

The generated project had a default-on `docker-compose` Maven profile (`spring-boot-docker-compose`) and
`spring.docker.compose.file: src/main/docker/no.yml`, a placeholder for "no database". The file does not exist, so
`./mvnw` (`spring-boot:run`) failed at startup with `'files' content [src/main/docker/no.yml] must exist`. Running
`java -jar` worked, because that optional dependency is not packaged, which is how the problem stayed hidden through
every earlier check. Both the profile and the property were removed; this service needs no supporting containers.

{: .lesson }
Verify the commands you document, exactly as documented. "The app runs" with `java -jar` said nothing about `./mvnw`.

## Developer environment

`.devcontainer/` defines a VS Code Dev Container (Java 21 with the Java and Spring Boot extension packs), so a new
team member can open the repository and build without installing a JDK locally.

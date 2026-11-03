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
export JAVA_HOME=$(/usr/libexec/java_home -F -v 21)     # macOS
```

{: .warning }
Use `-F` (fail fast). Without it, `java_home -v 21` silently returns **another** JDK (for example 25) when no JDK 21 is
registered with macOS, and the build then fails the enforcer check with a confusing message. A Homebrew JDK is not
registered automatically; register it once, without sudo:

```bash
ln -sfn /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk ~/Library/Java/JavaVirtualMachines/openjdk-21.jdk
/usr/libexec/java_home -F -v 21      # now prints the JDK 21 path
```

To make 21 the default for every new terminal, add the `export JAVA_HOME=...` line to `~/.zshrc`.

{: .note }
**IDE and Maven share `target/classes`.** VS Code's Java extension (and Eclipse) compile into the same folder with the
Eclipse compiler. Running `./mvnw` while the IDE is rebuilding can mix class files and produce errors such as
`NoClassDefFoundError: ...$1` in tests. Let the IDE finish, or set `"java.autobuild.enabled": false` in VS Code.

With SDKMAN instead:

```bash
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

## Annotation processors and Lombok

Two annotation processors run at compile time, **in this order**:

```xml
<annotationProcessorPaths>
    <path><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId></path>
    <path><groupId>org.springframework.boot</groupId><artifactId>spring-boot-configuration-processor</artifactId></path>
</annotationProcessorPaths>
```

Lombok goes first so later processors see the code it generates (if you add MapStruct, put its processor after Lombok,
with `lombok-mapstruct-binding`). `spring-boot-configuration-processor` generates
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
| `spotless-maven-plugin` | validate | **Fails the build** if Java code is not formatted (google-java-format); see below |
| `maven-surefire-plugin` | test | Unit tests (`*Test`) |
| `maven-failsafe-plugin` | integration-test, verify | Integration tests (`*IT`), run against the packaged classes |
| `jacoco-maven-plugin` | test, post-integration-test | Coverage reports for unit and integration tests |
| `modernizer-maven-plugin` | package | Flags legacy APIs that have modern JDK replacements |
| `sonar-maven-plugin` | on demand | Static analysis (settings in `sonar-project.properties`) |

### Code formatting: Spotless + google-java-format

All Java in `src/main/java` and `src/test/java` follows **[google-java-format](https://github.com/google/google-java-format)**
(Google style: 2-space indentation, 100 columns), plus removal of unused imports, trailing whitespace and a final newline.

```bash
./mvnw spotless:apply     # format everything (run before committing)
./mvnw spotless:check     # what the build runs
```

The build runs `spotless:check` in the **validate** phase, so unformatted code fails `./mvnw verify` immediately, before
compiling, naming the offending files. The build never rewrites sources itself: a formatter that silently changes files
during CI hides problems instead of reporting them. Generated sources (protoc output in `target/`) are excluded.

{: .lesson }
`google-java-format` **1.37.0** fails inside Spotless 3.10.x with a bare `InvocationTargetException` (the formatter
itself runs fine on its own; the plugin's integration does not support that release yet). The pom pins **1.36.1**,
with a comment saying why. Check before upgrading either one.

{: .tip }
IDE: install the google-java-format plugin (IntelliJ) or the "Language Support for Java" formatter with the Google style
(VS Code) so the editor formats the same way; `spotless:apply` remains the reference.

## Continuous integration (GitHub Actions)

`.github/workflows/ci.yml` runs on every push to `main`, every pull request, and on demand:

```mermaid
flowchart LR
    co[checkout] --> jdk["JDK 21 (Temurin)<br/>+ Maven cache"] --> verify["./mvnw -Pprod verify"] --> img["jib:buildTar<br/>(image built, not pushed)"] --> rep["upload test reports"]
```

`./mvnw -Pprod verify` is the same command developers run, so CI cannot pass for different reasons than a laptop does:
enforcer (JDK 21), `spotless:check`, protoc, unit tests, integration tests including Karate against **mocked** MBTA
APIs, Checkstyle and Modernizer.

| Design choice | Why |
|---|---|
| Actions pinned to a commit SHA (`actions/checkout@3d3c42e…  # v7.0.1`) | A tag can be moved to different code; a SHA cannot. Update deliberately. |
| `permissions: contents: read` | The job only reads the repository; least privilege for the token |
| `concurrency` with `cancel-in-progress` | A newer push to the same branch cancels the outdated run |
| `paths-ignore: docs/**, bruno/**, **/*.md` | Documentation changes do not need a build (GitHub Pages builds them separately) |
| No live MBTA tests | Third-party availability and its 20 requests/minute limit must not make CI flaky; run `KarateLiveIT` and Bruno on demand |
| Test reports uploaded even on failure (`if: always()`) | Diagnose failures from the run page: Surefire, Failsafe and Karate HTML reports |

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

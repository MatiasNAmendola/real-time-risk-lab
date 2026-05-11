# Integration Tests — Testcontainers Suite

## What is Testcontainers

Testcontainers is a Java library that spins up real Docker containers programmatically from JUnit tests. Each container is ephemeral: it starts at the beginning of a test class and is destroyed when the JVM exits (or sooner, when reuse is disabled). No shared external state, no flakiness from pre-existing data.

## Why it applies here

The risk-engine stack integrates with Postgres, Valkey, Tansu (ADR-0043), and Floci (unified AWS emulator, ADR-0042). Mocking these in unit tests is fast but proves nothing about wire compatibility, serialization, or connection-pool behaviour. This suite exercises the real clients against real (containerised) servers to catch integration bugs before they reach staging.

## How to run

Prerequisites: Docker (or OrbStack / Colima) must be running.

```bash
# From repo root
./scripts/integration-tests.sh

# Or directly from the module
cd tests/integration
./gradlew test jacocoTestReport -Pintegration
```

Surefire excludes `*IntegrationTest` by default. The `integration` Gradle profile activates Failsafe, which includes them in the `verify` phase.

To compile without running tests:

```bash
cd tests/integration
./gradlew shadowJar
```

## Containers and test mapping

| Container | Image | Tests |
|-----------|-------|-------|
| Postgres 16 | `postgres:16-alpine` | `OutboxFlushIntegrationTest`, `RiskDecisionE2EIntegrationTest` |
| Tansu | `ghcr.io/tansu-io/tansu:0.6.0` (memory storage) | `KafkaPublishConsumeIntegrationTest`, `OutboxFlushIntegrationTest`, `RiskDecisionE2EIntegrationTest` |
| Valkey 8 | `valkey/valkey:8-alpine` | `ValkeyIdempotencyIntegrationTest` |
| MinIO | `minio/minio:RELEASE.2024-11-07T00-52-20Z` | `AuditEventS3IntegrationTest`, `RiskDecisionE2EIntegrationTest` |
| Moto | `motoserver/moto:latest` | `MotoSecretsManagerIntegrationTest` |
| OpenBao | `openbao/openbao:2.1.0` | `OpenBaoSecretsIntegrationTest` |

## Performance: container reuse

By default Testcontainers tears down containers after each test run. Enable reuse so containers persist across Gradle invocations (saves ~10 s per run):

```bash
echo "testcontainers.reuse.enable=true" >> ~/.testcontainers.properties
```

Reuse relies on a hash of the container definition. Changing the image tag or environment variables automatically starts a fresh container.

All containers in `IntegrationTestSupport` are declared `static`, so within a single Gradle run they are shared across test classes in the same classloader — only one Postgres container starts regardless of how many test classes reference it.

## Adding a new test

1. Identify which container(s) you need (see table above).
2. Create a class in the appropriate package under `src/test/java/io/riskplatform/integration/`.
3. Annotate the class with `@Testcontainers` and extend `IntegrationTestSupport`.
4. Declare only the containers you need as `@Container static final` fields, referencing the shared instances from the parent class.
5. Write `@Test` methods using AssertJ assertions.
6. Add your test to the table in this README.

Example skeleton:

```java
@Testcontainers
class MyNewIntegrationTest extends IntegrationTestSupport {

    @Container
    static final PostgreSQLContainer<?> PG = postgres;

    @Test
    void my_scenario() throws Exception {
        // use PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()
    }
}
```

## Limitations

- Docker must be running locally. The script at `scripts/integration-tests.sh` exits with code 2 and a clear error message if Docker is not available.
- In CI, replace the local Docker daemon with [Testcontainers Cloud](https://testcontainers.com/cloud/) to avoid privileged containers. Configuration is outside the scope of this module.
- Java 21 LTS is the executable baseline (`--release 21`); set `JAVA_HOME` to JDK 21+ before running. Java 25 is optional as a runtime target only.

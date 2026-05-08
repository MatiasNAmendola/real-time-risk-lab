# Multi-stage: build stage stages the Gradle shadowJar, runtime stage runs it.
# `./gradlew :poc:java-vertx-distributed:controller-app:shadowJar` produces the fat jar
# at controller-app/build/libs/controller-app-<version>.jar before `docker compose build`.

# ---- Build stage ----
# Build context is the project root (set in docker-compose.yml context: .)
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace

# Copy the pre-built fat jar (produced by Gradle shadowJar)
# Path is relative to build context = project root
COPY controller-app/build/libs/controller-app-0.1.0-SNAPSHOT.jar app.jar

# ---- Runtime stage ----
FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app

# Non-root user with UID 1001 (distinct per service)
RUN addgroup -g 1001 appgroup && adduser -u 1001 -G appgroup -D appuser
USER 1001:1001

COPY --from=build /workspace/app.jar app.jar
COPY hazelcast/hazelcast.xml /etc/riskplatform/hazelcast/hazelcast.xml
COPY otel/opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar
COPY jacoco-agent/jacocoagent.jar /jacoco/jacocoagent.jar

EXPOSE 8080 6300

ENTRYPOINT ["java", \
  "-javaagent:/jacoco/jacocoagent.jar=output=tcpserver,address=*,port=6300,append=true,includes=io.riskplatform.*", \
  "-javaagent:/otel/opentelemetry-javaagent.jar", \
  "-Dhazelcast.local.publicAddress=controller-app:5701", \
  "-jar", "app.jar"]

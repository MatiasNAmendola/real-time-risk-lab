# ---- Build stage ----
# Build context is the project root (set in docker-compose.yml context: .)
# Java version note: Gradle produces Java 21 bytecode (`--release 21`).
# The Temurin 25 images here are runtime/container images only;
# they do not mean this module builds classfile 25.
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace
COPY repository-app/build/libs/repository-app-0.1.0-SNAPSHOT.jar app.jar

# ---- Runtime stage ----
FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app

# Non-root user with UID 1003 (distinct from controller 1001, usecase 1002)
RUN addgroup -g 1003 appgroup && adduser -u 1003 -G appgroup -D appuser
USER 1003:1003

COPY --from=build /workspace/app.jar app.jar
COPY hazelcast/hazelcast.xml /etc/riskplatform/hazelcast/hazelcast.xml
COPY otel/opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar
COPY jacoco-agent/jacocoagent.jar /jacoco/jacocoagent.jar

EXPOSE 6300

ENTRYPOINT ["java", \
  "-javaagent:/jacoco/jacocoagent.jar=output=tcpserver,address=*,port=6300,append=true,includes=io.riskplatform.*", \
  "-javaagent:/otel/opentelemetry-javaagent.jar", \
  "-Dhazelcast.local.publicAddress=repository-app:5701", \
  "-jar", "app.jar"]

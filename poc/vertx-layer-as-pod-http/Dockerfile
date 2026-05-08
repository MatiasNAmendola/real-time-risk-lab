##############################################################################
# vertx-layer-as-pod-http — multi-stage Dockerfile
#
# A single fat-jar handles all 3 pods; the role is selected via CMD arg.
# Build context must be the poc/vertx-layer-as-pod-http directory (or repo root).
#
# Build (from repo root):
#   ./gradlew :poc:vertx-layer-as-pod-http:shadowJar
#   docker build -f poc/vertx-layer-as-pod-http/Dockerfile poc/vertx-layer-as-pod-http -t riskplatform/vrp-pod:latest
#
# Or via compose override (see compose.override.yml).
##############################################################################

# ── Stage 1: builder ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copy the fat-jar produced by shadowJar
COPY build/libs/vertx-layer-as-pod-http.jar app.jar

# ── Stage 2: runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS runtime

# Create a non-root group and user per pod (UIDs set at compose level via user: field)
# UIDs 1101-1103 are reserved for this PoC (1001-1004 used by vertx-layer-as-pod-eventbus)
RUN groupadd -g 1100 vrp && \
    useradd -u 1101 -g vrp -s /bin/sh -M vrp-controller && \
    useradd -u 1102 -g vrp -s /bin/sh -M vrp-usecase && \
    useradd -u 1103 -g vrp -s /bin/sh -M vrp-repository

WORKDIR /app
COPY --from=builder /build/app.jar app.jar

# JVM flags: small heap for PoC, enable virtual thread diagnostics
ENV JAVA_TOOL_OPTIONS="-Xms64m -Xmx256m"

# POD_ROLE is set by the compose service or k8s deployment
ENV POD_ROLE=controller

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
CMD ["controller"]

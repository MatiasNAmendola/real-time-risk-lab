# ---- Build stage ----
# Build context is the project root (set in docker-compose.yml context: .)
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace
COPY usecase-app/build/libs/usecase-app-0.1.0-SNAPSHOT.jar app.jar

# ---- Runtime stage ----
FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app

# Non-root user with UID 1002 (distinct from controller 1001, repository 1003)
RUN addgroup -g 1002 appgroup && adduser -u 1002 -G appgroup -D appuser
USER 1002:1002

COPY --from=build /workspace/app.jar app.jar
COPY hazelcast/hazelcast.xml /etc/riskplatform/hazelcast/hazelcast.xml
COPY otel/opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar
COPY jacoco-agent/jacocoagent.jar /jacoco/jacocoagent.jar

EXPOSE 6300

ENTRYPOINT ["java", \
  "-javaagent:/jacoco/jacocoagent.jar=output=tcpserver,address=*,port=6300,append=true,includes=io.riskplatform.*", \
  "-javaagent:/otel/opentelemetry-javaagent.jar", \
  "-Dhazelcast.local.publicAddress=usecase-app:5701", \
  "-jar", "app.jar"]

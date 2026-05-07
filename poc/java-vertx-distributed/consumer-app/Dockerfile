# ---- Build stage ----
# Build context is the project root (set in docker-compose.yml context: .)
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace
COPY consumer-app/build/libs/consumer-app-0.1.0-SNAPSHOT.jar app.jar

# ---- Runtime stage ----
FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app

# Non-root user with UID 1004 (distinct per service)
RUN addgroup -g 1004 appgroup && adduser -u 1004 -G appgroup -D appuser
USER 1004:1004

COPY --from=build /workspace/app.jar app.jar
COPY otel/opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar
COPY jacoco-agent/jacocoagent.jar /jacoco/jacocoagent.jar

EXPOSE 6300

ENTRYPOINT ["java", \
  "-javaagent:/jacoco/jacocoagent.jar=output=tcpserver,address=*,port=6300,append=true,includes=com.naranjax.*", \
  "-javaagent:/otel/opentelemetry-javaagent.jar", \
  "-jar", "app.jar"]

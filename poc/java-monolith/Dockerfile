FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -g 1005 appgroup && adduser -u 1005 -G appgroup -D appuser
USER 1005:1005
COPY build/libs/java-monolith.jar app.jar
COPY ../../otel/opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar
EXPOSE 8090
ENTRYPOINT ["java", \
  "-javaagent:/otel/opentelemetry-javaagent.jar", \
  "-Xms128m", "-Xmx768m", \
  "-jar", "app.jar"]

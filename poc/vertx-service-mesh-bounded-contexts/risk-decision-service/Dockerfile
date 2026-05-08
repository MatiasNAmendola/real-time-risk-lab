FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace
COPY risk-decision-service/build/libs/risk-decision-service-0.1.0-SNAPSHOT.jar app.jar

FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -g 1101 appgroup && adduser -u 1101 -G appgroup -D appuser
USER 1101:1101
COPY --from=build /workspace/app.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace
COPY fraud-rules-service/build/libs/fraud-rules-service-0.1.0-SNAPSHOT.jar app.jar

FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -g 1102 appgroup && adduser -u 1102 -G appgroup -D appuser
USER 1102:1102
COPY --from=build /workspace/app.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]

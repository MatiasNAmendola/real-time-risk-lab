FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace
COPY ml-scorer-service/build/libs/ml-scorer-service-0.1.0-SNAPSHOT.jar app.jar

FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -g 1103 appgroup && adduser -u 1103 -G appgroup -D appuser
USER 1103:1103
COPY --from=build /workspace/app.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]

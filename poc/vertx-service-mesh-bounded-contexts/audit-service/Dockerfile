FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace
COPY audit-service/build/libs/audit-service-0.1.0-SNAPSHOT.jar app.jar

FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -g 1104 appgroup && adduser -u 1104 -G appgroup -D appuser
USER 1104:1104
COPY --from=build /workspace/app.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]

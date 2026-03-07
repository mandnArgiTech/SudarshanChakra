# Multi-stage Docker build for Spring Boot microservice
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
COPY api-gateway/build.gradle.kts api-gateway/build.gradle.kts
COPY api-gateway/src api-gateway/src
RUN chmod +x gradlew && ./gradlew :api-gateway:bootJar -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/api-gateway/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

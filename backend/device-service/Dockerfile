# Multi-stage Docker build for Spring Boot microservice
# Context must be backend/: docker build -f backend/device-service/Dockerfile backend/
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
COPY jwt-support/build.gradle.kts jwt-support/build.gradle.kts
COPY jwt-support/src jwt-support/src
COPY device-service/build.gradle.kts device-service/build.gradle.kts
COPY device-service/src device-service/src
RUN chmod +x gradlew && ./gradlew :device-service:bootJar -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/device-service/build/libs/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]

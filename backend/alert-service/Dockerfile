# Multi-stage Docker build for Spring Boot microservice
# Build with context backend/: docker build -f backend/alert-service/Dockerfile backend/
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
COPY jwt-support/build.gradle.kts jwt-support/build.gradle.kts
COPY jwt-support/src jwt-support/src
COPY alert-service/build.gradle.kts alert-service/build.gradle.kts
COPY alert-service/src alert-service/src
RUN chmod +x gradlew && ./gradlew :alert-service:bootJar -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/alert-service/build/libs/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]

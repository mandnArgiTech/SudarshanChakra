# Multi-stage Docker build for Spring Boot microservice
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
COPY auth-service/build.gradle.kts auth-service/build.gradle.kts
COPY auth-service/src auth-service/src
RUN chmod +x gradlew && ./gradlew :auth-service:bootJar -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/auth-service/build/libs/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]

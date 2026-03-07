# Multi-stage Docker build for Spring Boot microservice
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
COPY siren-service/build.gradle.kts siren-service/build.gradle.kts
COPY siren-service/src siren-service/src
RUN chmod +x gradlew && ./gradlew :siren-service:bootJar -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/siren-service/build/libs/*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]

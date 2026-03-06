# Multi-stage Docker build for Spring Boot microservice
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY ../gradlew ../gradle ./
COPY ../build.gradle.kts ../settings.gradle.kts ./
COPY build.gradle.kts .
COPY src ./src
RUN chmod +x gradlew && ./gradlew bootJar -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]

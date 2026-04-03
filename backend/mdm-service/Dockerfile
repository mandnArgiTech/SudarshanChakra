# Multi-stage Docker build for Spring Boot microservice
# Build with context backend/: docker build -f backend/mdm-service/Dockerfile backend/
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
COPY mdm-service/build.gradle.kts mdm-service/build.gradle.kts
COPY mdm-service/src mdm-service/src
RUN chmod +x gradlew && ./gradlew :mdm-service:bootJar -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/mdm-service/build/libs/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]

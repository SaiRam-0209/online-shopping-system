FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
CMD ["echo", "Mock Spring Boot startup for api-gateway"]

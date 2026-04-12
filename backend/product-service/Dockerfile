FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Uncomment the following line to build the JAR during image creation if pom is fully configured
# RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# Mock command since it's a structural scaffolding
CMD ["echo", "Mock Spring Boot startup for product-service"]

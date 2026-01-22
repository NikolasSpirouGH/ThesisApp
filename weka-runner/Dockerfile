# Multi-stage build for Weka Runner

# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /build

# Copy pom.xml first (for dependency caching)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Install necessary utilities
RUN apk add --no-cache bash

# Copy the uber-jar from builder
COPY --from=builder /build/target/weka-runner-*.jar /app/weka-runner.jar

# Set environment variables
ENV DATA_DIR=/data
ENV MODEL_DIR=/model

# Default command (can be overridden)
ENTRYPOINT ["java", "-Xmx2g", "-jar", "/app/weka-runner.jar"]
CMD ["train"]

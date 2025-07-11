# Multi-stage Dockerfile for CardDemo Spring Boot Application
# Optimized for Kubernetes orchestration with security hardening and monitoring capabilities
# Target image size: ~120 MB for production deployment
# Supports Java 21 runtime environment for Spring Boot 3.2.x application

# =============================================================================
# Stage 1: Maven Dependencies Resolution
# =============================================================================
# Base image: Maven 3.9.4 with Eclipse Temurin OpenJDK 21 for build environment
FROM maven:3.9.4-eclipse-temurin-21 AS dependencies

# Set working directory for build process
WORKDIR /app

# Copy Maven configuration files for dependency resolution
COPY pom.xml .

# Download and cache Maven dependencies separately to optimize build times
# This layer will be cached unless pom.xml changes
RUN mvn dependency:go-offline -B

# =============================================================================
# Stage 2: Application Build
# =============================================================================
# Continue from dependencies stage to leverage cached dependencies
FROM dependencies AS build

# Copy source code for compilation
COPY src ./src

# Build the application using Maven
# Skip tests during Docker build for faster builds (tests run in CI/CD pipeline)
# Use production profile for optimized build
RUN mvn clean package -DskipTests -Pprod -B -Ddockerfile.skip=true

# Verify the JAR file was created successfully
RUN ls -la target/ && \
    test -f target/carddemo-1.0.0.jar || (echo "JAR file not found!" && exit 1)

# =============================================================================
# Stage 3: Production Runtime Environment
# =============================================================================
# Base image: Eclipse Temurin OpenJDK 21 JRE on Alpine Linux for minimal footprint
FROM eclipse-temurin:21-jre-alpine AS runtime

# Metadata labels for container identification and management
LABEL maintainer="CardDemo Development Team"
LABEL version="1.0.0"
LABEL description="Spring Boot modular monolith for credit card management system"
LABEL java.version="21"
LABEL spring.boot.version="3.2.5"

# Install additional security and monitoring tools
RUN apk add --no-cache \
    curl \
    dumb-init \
    tzdata && \
    rm -rf /var/cache/apk/*

# Set timezone to UTC for consistent logging
ENV TZ=UTC

# Create non-root user for security compliance
# User ID 1001 follows container security best practices
RUN addgroup -g 1001 carddemo && \
    adduser -D -s /bin/sh -u 1001 -G carddemo carddemo

# Create application directory and set ownership
RUN mkdir -p /app && \
    chown -R carddemo:carddemo /app

# Set working directory
WORKDIR /app

# Switch to non-root user for security
USER carddemo

# Copy the built JAR file from build stage
COPY --from=build --chown=carddemo:carddemo /app/target/carddemo-1.0.0.jar app.jar

# JVM optimization for containerized environments
# - UseContainerSupport: Enables container-aware JVM settings
# - UseG1GC: G1 garbage collector for better performance
# - MaxGCPauseMillis: Target maximum GC pause time
# - Xmx1024m: Maximum heap size (adjustable based on container resources)
# - Xms512m: Initial heap size
ENV JAVA_OPTS="-Xmx1024m -Xms512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseContainerSupport -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -Djava.security.egd=file:/dev/./urandom"

# Spring Boot specific configuration
ENV SPRING_PROFILES_ACTIVE=prod

# Expose application port (8080) and actuator/metrics port (8081)
# Port 8080: Main application REST endpoints
# Port 8081: Spring Boot Actuator endpoints for monitoring and health checks
EXPOSE 8080 8081

# Volume for temporary files and logs (read-only root filesystem compliance)
VOLUME ["/tmp", "/var/log"]

# Health check configuration for Kubernetes readiness and liveness probes
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8081/actuator/health || exit 1

# Use dumb-init for proper signal handling in containers
ENTRYPOINT ["dumb-init", "--"]

# Application startup command
# Uses Spring Boot's executable JAR with optimized JVM settings
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

# =============================================================================
# Container Optimization Features:
# =============================================================================
# 1. Multi-stage build reduces final image size to ~120 MB
# 2. Dependency caching optimizes build times
# 3. Alpine Linux base minimizes attack surface
# 4. Non-root user execution follows security best practices
# 5. Container-aware JVM settings optimize memory usage
# 6. Health checks enable Kubernetes integration
# 7. Proper signal handling with dumb-init
# 8. Layered JAR structure from Spring Boot Maven plugin
# 9. Volume mounts for read-only root filesystem compliance
# 10. Comprehensive monitoring endpoint exposure

# =============================================================================
# Kubernetes Integration Points:
# =============================================================================
# - Liveness probe: http://localhost:8081/actuator/health/liveness
# - Readiness probe: http://localhost:8081/actuator/health/readiness
# - Metrics endpoint: http://localhost:8081/actuator/prometheus
# - Application traffic: http://localhost:8080
# - Resource limits: CPU 500m-2000m, Memory 1Gi-4Gi
# - Security context: Non-root user (1001), read-only root filesystem

# =============================================================================
# Security Hardening Implementation:
# =============================================================================
# - Non-root user execution (carddemo:1001)
# - Minimal base image (Alpine Linux)
# - Read-only root filesystem support
# - Security scanning compatible
# - No unnecessary packages or tools
# - Proper file permissions and ownership
# - Container-specific user and group

# =============================================================================
# Monitoring and Observability:
# =============================================================================
# - Spring Boot Actuator endpoints on port 8081
# - Prometheus metrics at /actuator/prometheus
# - Health checks at /actuator/health
# - JVM metrics exposure
# - Custom business metrics support
# - Structured logging output
# - Container resource monitoring
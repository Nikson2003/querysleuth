# syntax=docker/dockerfile:1
# Multi-stage Dockerfile optimized for low-memory free hosting platforms (Render, Railway, Koyeb, Fly.io)

# ==========================================
# 1. BUILD STAGE
# ==========================================
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build executable JAR
COPY src ./src
RUN mvn clean package -DskipTests -B

# ==========================================
# 2. RUNTIME STAGE
# ==========================================
FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

# Create a non-privileged user for container security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy compiled jar from builder stage
COPY --from=builder /app/target/querysleuth-backend-0.0.1-SNAPSHOT.jar app.jar
RUN chown -R appuser:appgroup /app

USER appuser

# Expose default port
EXPOSE 8080

# Environment configurations:
# - MaxRAMPercentage=75.0 keeps heap usage safely within free-tier limits (e.g. 512MB RAM)
# - SerialGC reduces GC thread memory overhead in single-core/low-RAM containers
ENV PORT=8080 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseSerialGC -XshowSettings:vm"

# Healthcheck allows platforms to verify container health
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD wget -qO- http://localhost:${PORT}/api/v1/health | grep -q "UP" || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

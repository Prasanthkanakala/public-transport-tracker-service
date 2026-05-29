# ── Build stage ───────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /app

# Copy only dependency resolution files first for layer caching
COPY settings.gradle build.gradle ./
COPY gradle/ gradle/ 2>/dev/null || true

# Resolve and cache dependencies (without source)
RUN apt-get update -qq && apt-get install -y --no-install-recommends wget unzip \
    && wget -q https://services.gradle.org/distributions/gradle-8.5-bin.zip -P /tmp \
    && unzip -q /tmp/gradle-8.5-bin.zip -d /opt \
    && ln -s /opt/gradle-8.5/bin/gradle /usr/local/bin/gradle \
    && apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/*.zip

# Copy source and build
COPY src/ src/
RUN gradle bootJar --no-daemon -q

# ── Runtime stage ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy AS runtime

LABEL maintainer="transport-tracker@example.com"
LABEL version="1.0.0"
LABEL description="Public Transport Tracker – Spring Boot backend"

# Create non-root user (security best practice)
RUN groupadd --system appgroup && useradd --system --gid appgroup appuser

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

# Own the jar
RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8080

# Tune JVM for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 \
               -XX:+ExitOnOutOfMemoryError \
               -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dspring.profiles.active=${SPRING_PROFILE:-prod} -jar app.jar"]
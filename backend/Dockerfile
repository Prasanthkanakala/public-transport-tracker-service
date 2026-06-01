# Runtime image only
FROM eclipse-temurin:17-jre-jammy

LABEL maintainer="transport-tracker@example.com"
LABEL description="Public Transport Tracker Backend"

WORKDIR /app

# Copy JAR produced by Jenkins build stage
COPY build/libs/*.jar app.jar

# Create non-root user
RUN groupadd --system appgroup && \
    useradd --system --gid appgroup appuser && \
    chown appuser:appgroup app.jar

USER appuser

EXPOSE 8081

ENV JAVA_OPTS="-XX:+UseContainerSupport \
-XX:MaxRAMPercentage=75.0 \
-XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh","-c","java $JAVA_OPTS -Dspring.profiles.active=${SPRING_PROFILE:-prod} -jar app.jar"]
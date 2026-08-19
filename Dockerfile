# Build stage
FROM maven:3.9.4-amazoncorretto-17 AS build

WORKDIR /app

# Copy pom.xml and download dependencies (for better caching)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime stage
FROM amazoncorretto:17-alpine3.18

# Install curl for health checks
RUN apk add --no-cache curl bash tzdata

# Create non-root user
RUN addgroup -g 1001 autotrack && \
    adduser -D -s /bin/bash -u 1001 -G autotrack autotrack

# Set working directory
WORKDIR /app

# Copy JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# Change ownership
RUN chown -R autotrack:autotrack /app

# Switch to non-root user
USER autotrack

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f https://brings-broadcasting-tucson-fioricet.trycloudflare.com/actuator/health || exit 1

# Expose port
EXPOSE 5000

# Set JVM options for containerized environment
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

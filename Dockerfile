# =============================================================================
# ShareWay Booking & Pricing - multi-stage image (AGENTS.md > Deploy)
# Stage 1 builds the jar with Maven + JDK 21; stage 2 ships only the JRE and the jar.
# No secrets and no configuration are baked into the image: DB_URL, DB_USER,
# DB_PASSWORD, SERVER_PORT and SPRING_PROFILES_ACTIVE arrive as environment
# variables when the container starts.
# =============================================================================

# ---------- Stage 1: build (Maven + JDK 21) ----------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

# pom.xml first and alone: Docker keeps this layer as cache, so dependencies are
# only re-downloaded when the pom actually changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Sources second (they change more often than the dependencies).
COPY src ./src

# Package without tests: the test suite runs in CI, not inside the image build.
RUN mvn -B -DskipTests package

# ---------- Stage 2: runtime (JRE 21 + jar only) ----------
FROM eclipse-temurin:21-jre

# curl is required by HEALTHCHECK; apt lists are removed so the layer stays small.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Non-root system user: the process never runs as root.
RUN groupadd --system app \
    && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app

WORKDIR /app

# Only the executable fat jar is copied; the wildcard fails the build if the
# package step did not produce exactly one jar.
COPY --from=build /workspace/target/*.jar /app/app.jar

# From here on, everything runs as the unprivileged user.
USER app:app

# Container port; the real port comes from SERVER_PORT at run time.
EXPOSE 8080

# Docker probes /actuator/health; the container is marked unhealthy when it fails.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

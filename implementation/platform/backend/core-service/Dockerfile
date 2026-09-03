# EN002 — Backend container image for `core-service`.
#
# Multi-stage (OD-1): the build stage uses Maven + a full JDK; the runtime stage carries only a
# JRE, the application jar, and `curl` (for the Compose health probe). No Maven, no source tree,
# no credentials in the final image. The image only PACKAGES the existing service — it introduces
# no business behaviour and does not change the backend architecture (ADR-001 unchanged).

# ---------- build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Warm the dependency cache as its own layer, using the pinned Maven wrapper (EN003 / ADR-003).
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline

# Compile + package (unit/integration tests run on the host build, not here).
COPY src ./src
RUN ./mvnw -B -q clean package -DskipTests \
    && cp target/core-service-*.jar /workspace/app.jar

# ---------- runtime ----------
FROM eclipse-temurin:21-jre-jammy AS runtime

# curl is used only by the container health check; keep the layer minimal.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Run as a non-root user.
RUN useradd --system --uid 1001 --create-home --home-dir /app appuser
WORKDIR /app
COPY --from=build /workspace/app.jar /app/app.jar
USER appuser

EXPOSE 8080

# Container-level probe (Compose also defines its own health check).
HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=6 \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

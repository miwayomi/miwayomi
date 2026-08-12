# syntax=docker/dockerfile:1
#
# miwayomi — headless server image (no Chromium; Cloudflare is handled by
# FlareSolverr only, see docker-compose.yml / docker/flaresolverr.Dockerfile).

# ---- Stage 1: build the fat JAR (JDK 21 + Gradle wrapper) ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src

# Copy the Gradle wrapper and project definition first for better layer caching.
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle

# Copy the modules the server build needs.
COPY source-api ./source-api
COPY core-common ./core-common
COPY android-compat ./android-compat
COPY server ./server

RUN ./gradlew :server:shadowJar --no-daemon --console=plain

# ---- Stage 2: runtime (JRE only — no JDK, no Chromium) ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# curl is used by the HEALTHCHECK.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /src/server/build/libs/miwayomi-all.jar /app/miwayomi-all.jar

# Non-root user (extensions are third-party code; run least-privilege).
RUN useradd --home-dir /app --shell /bin/sh -u 10001 miwayomi \
    && mkdir -p /data \
    && chown -R miwayomi:miwayomi /app /data

# Persistent state: SQLite DB, extensions, cookies, caches.
VOLUME ["/data"]

EXPOSE 4567

# Same small-heap flags as start.sh; tune with -e JAVA_OPTS="-Xmx768m ...".
ENV JAVA_OPTS="-Xmx512m -Xms64m -XX:MaxMetaspaceSize=256m -XX:+UseSerialGC"

# FlareSolverr URL is injected via the FLARESOLVERR_URL env var (defaults to the
# compose service name). Pass --flaresolverr "" (empty) to disable it.
ENV FLARESOLVERR_URL="http://flaresolverr:8191"

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -fsS http://127.0.0.1:4567/api/v1/health || exit 1

USER miwayomi
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/miwayomi-all.jar --host 0.0.0.0 --port 4567 --data /data --no-open --flaresolverr ${FLARESOLVERR_URL}"]

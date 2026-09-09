# syntax=docker/dockerfile:1

# One recipe for every service; the service is chosen at build time:
#   docker build --build-arg SERVICE=pollution-api-service -t pollution-api-service .
# Only the runtime stage depends on it, so the build stage is the same for all five images
# and a layer cache serves it once. See "Images and CI" in CLAUDE.md.

# ---- build: the whole reactor. Tests are compiled (the test-jars are needed) but not run;
#      CI runs them before any image is built. ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -ntp -DskipTests package

# ---- runtime: one service's jar and its dependencies, run by an unprivileged user ----
FROM eclipse-temurin:21-jre
ARG SERVICE
RUN useradd --system --uid 10001 --user-group --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
# the dependencies as one layer and the service's own jar as another:
# a code change rebuilds and ships only the small one
COPY --from=build /src/${SERVICE}/target/lib/ lib/
COPY --from=build /src/${SERVICE}/target/${SERVICE}.jar app.jar
USER app
# JSON lines on stdout and no log file: what a container platform collects. Everything else
# (HEALTH_PORT, the stores' addresses, secrets) comes from the manifest that runs the
# container; more JVM flags go in JAVA_TOOL_OPTIONS.
ENV LOG_FORMAT=json
# the heap follows the container's memory limit, and an OutOfMemoryError ends the process
# so the platform restarts it instead of keeping a JVM that can no longer allocate
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]

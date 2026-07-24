# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle

RUN --mount=type=cache,target=/root/.gradle \
    chmod +x gradlew \
    && ./gradlew dependencies \
        --configuration runtimeClasspath \
        --no-daemon \
        --quiet >/dev/null

COPY src ./src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew clean bootJar --no-daemon \
    && cp build/libs/PL_Timetable_project-0.0.1-SNAPSHOT.jar /workspace/app.jar


FROM eclipse-temurin:17-jre-jammy AS runtime

ARG APP_VERSION=0.0.1-SNAPSHOT
ARG APP_COMMIT=unknown

LABEL org.opencontainers.image.title="PL Timetable API" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.revision="${APP_COMMIT}"

RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app --home-dir /app --shell /usr/sbin/nologin app

WORKDIR /app

COPY --from=builder --chown=app:app /workspace/app.jar /app/app.jar

USER 10001:10001

ENV SERVER_PORT=8080 \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
    CMD curl --fail --silent --show-error \
        http://127.0.0.1:8080/api/v1/health/live >/dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# ResearchPath backend image (Railway builds this with the service root directory set to /backend).
# Tests are not run here: CI (`mvnw clean verify`, Testcontainers) is the release gate for the same commit.
# Base images are pinned by digest; update them deliberately (Temurin 21.0.12 at the time of pinning).

FROM eclipse-temurin:21-jdk@sha256:23cc5734ff46e776f51ce66eb9ee08444fd47caabec0a95b7eb6e94be2d3696d AS build
WORKDIR /workspace
# Dependencies first so they are cached across source-only changes.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN sh mvnw -B -ntp -q dependency:go-offline
COPY src src
RUN sh mvnw -B -ntp -q -DskipTests package \
    && cp target/researchpath-backend-*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre@sha256:227118764cccd3c3294617061aaa93a08309c63a395eefa83e6b35032992ea99
RUN groupadd --system app && useradd --system --gid app --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build --chown=root:root --chmod=0444 /workspace/app.jar /app/app.jar
USER app
# Container-aware heap; restart on OOM instead of limping on. The platform supplies PORT and SPRING_PROFILES_ACTIVE.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

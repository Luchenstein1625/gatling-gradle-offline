# ==============================================================================
# STAGE 1: Dependency Cache (desde carpeta offline)
# ==============================================================================
# CHANGESET-DOCKERFILE-2026-07-20:
# Subir este archivo para imprimir BUILD_CHANGELOG en docker compose build.
FROM eclipse-temurin:11-jdk-jammy AS dependency-cache

ENV GRADLE_USER_HOME=/opt/gradle-cache
WORKDIR /workspace

# Requiere artefactos offline previamente preparados en el repo (archivos por partes).
COPY offline-deps/gradle/gradle-8.6.tar.gz.part-* /tmp/
COPY offline-deps/gradle/gradle-cache.tar.gz.part-* /tmp/

RUN mkdir -p /opt \
 && cat $(ls /tmp/gradle-8.6.tar.gz.part-* | sort) > /tmp/gradle-8.6.tar.gz \
 && cat $(ls /tmp/gradle-cache.tar.gz.part-* | sort) > /tmp/gradle-cache.tar.gz \
 && tar -xzf /tmp/gradle-8.6.tar.gz -C /opt \
 && tar -xzf /tmp/gradle-cache.tar.gz -C /opt \
 && test -d /opt/gradle/gradle-8.6 \
 && test -d /opt/gradle-cache \
 && rm -f /tmp/gradle-8.6.tar.gz /tmp/gradle-cache.tar.gz /tmp/gradle-8.6.tar.gz.part-* /tmp/gradle-cache.tar.gz.part-*

ENV PATH="/opt/gradle/gradle-8.6/bin:${PATH}"

# ==============================================================================
# STAGE 2: Builder (Java 11)
# ==============================================================================
FROM eclipse-temurin:11-jdk-jammy AS builder

ENV GRADLE_USER_HOME=/opt/gradle-cache
WORKDIR /workspace

COPY --from=dependency-cache /opt/gradle /opt/gradle
COPY --from=dependency-cache /opt/gradle-cache /opt/gradle-cache
ENV PATH="/opt/gradle/gradle-8.6/bin:${PATH}"

COPY BUILD_CHANGELOG.md /workspace/BUILD_CHANGELOG.md
COPY app/ ./app/

RUN printf '\n=== BUILD CHANGELOG ===\n' \
 && cat /workspace/BUILD_CHANGELOG.md \
 && printf '=== END BUILD CHANGELOG ===\n\n' \
 && cd app \
 && gradle --offline --no-daemon bootJar --stacktrace

# ==============================================================================
# STAGE 3: Runtime (Java 11)
# ==============================================================================
FROM eclipse-temurin:11-jre-jammy AS runtime

# En STAGE 3 (Runtime) de tu Dockerfile:
ENV API_PREFIX=/gatling/gatling-gen3-app/v0.1 \
    SERVER_SERVLET_CONTEXT_PATH=/gatling/gatling-gen3-app/v0.1
    
RUN groupadd -g 1001 gatling && \
    useradd -u 1001 -g gatling -m -d /home/gatling gatling

ENV APP_DATA_ROOT=/app/data \
    RESULTS_DIR=/app/data/executions \
    HOME=/home/gatling \
    JAVA_TOOL_OPTIONS="-XX:+UseG1GC -Xms32m -Xmx160m -XX:MaxMetaspaceSize=128m"

WORKDIR /app

# Copiamos el JAR resultante
COPY --from=builder /workspace/app/build/libs/app.jar /app/app.jar

RUN mkdir -p /app/data/configurations /app/data/executions && \
    chown -R gatling:gatling /app

USER gatling
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
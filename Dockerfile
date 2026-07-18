ARG BASE_IMAGE=eclipse-temurin:21.0.11_10-jdk-jammy

FROM ${BASE_IMAGE} AS builder
ARG GRADLE_VERSION=8.6

ENV GRADLE_USER_HOME=/opt/offline/gradle-cache
WORKDIR /workspace

# Ensamblar gradle zip desde partes (dividido en <50MB para git)
COPY offline/gradle/gradle-${GRADLE_VERSION}-bin.zip.part.* /tmp/gradle-parts/
RUN cat /tmp/gradle-parts/gradle-${GRADLE_VERSION}-bin.zip.part.* > /tmp/gradle.zip \
 && rm -rf /tmp/gradle-parts/
RUN mkdir -p /opt/gradle \
 && cd /opt/gradle \
 && jar -xf /tmp/gradle.zip \
 && chmod +x /opt/gradle/gradle-${GRADLE_VERSION}/bin/gradle \
 && rm /tmp/gradle.zip

ENV PATH="/opt/gradle/gradle-${GRADLE_VERSION}/bin:${PATH}"

COPY offline/gradle-cache/ ${GRADLE_USER_HOME}/
COPY settings.gradle build.gradle gradle.properties ./

RUN mkdir -p gradle/wrapper && \
    /opt/gradle/gradle-8.6/bin/gradle wrapper --gradle-version 8.6 2>&1 | tail -5

COPY src/ ./src/

# First build: ONLINE mode to download CVE-patched dependency versions
# (Log4j 2.25.4, Jackson 2.21.4, Commons-lang3 3.17.1)
RUN gradle --no-daemon --refresh-dependencies clean build prepareGatlingRuntime 2>&1 | tail -20


FROM ${BASE_IMAGE} AS runtime

# Crear usuario no-root para seguridad
RUN groupadd -g 1001 gatling && \
    useradd -u 1001 -g gatling -m -d /home/gatling gatling

ENV APP_DATA_ROOT=/app/data \
    GATLING_CLASSPATH=/opt/gatling/classes:/opt/gatling/lib/* \
    RESULTS_DIR=/app/data/executions \
    HOME=/home/gatling \
    JAVA_TOOL_OPTIONS="-XX:+UseG1GC -Xms32m -Xmx160m -XX:MaxMetaspaceSize=128m"

WORKDIR /app

COPY --from=builder /workspace/build/libs/gatling-control-api.jar /app/app.jar
COPY --from=builder /workspace/build/gatling-runtime/classes/ /opt/gatling/classes/
COPY --from=builder /workspace/build/gatling-runtime/lib/ /opt/gatling/lib/
COPY --from=builder /workspace/gradlew /workspace/gradlew.bat /app/
COPY --from=builder /workspace/gradle/ /app/gradle/
COPY --from=builder /workspace/settings.gradle /workspace/build.gradle /workspace/gradle.properties /app/

RUN chmod +x /app/gradlew

RUN mkdir -p /app/data/configurations /app/data/executions && \
    chown -R gatling:gatling /app /opt/gatling

# Cambiar a usuario no-root
USER gatling

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=20s --retries=3 \
    CMD curl -sf http://localhost:8080/gatling/gatling-gen3-app/v0.1/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

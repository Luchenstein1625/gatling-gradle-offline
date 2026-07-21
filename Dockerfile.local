FROM aquasec/trivy:0.72.0 AS trivy

FROM eclipse-temurin:17-jdk-jammy AS dependency-cache

ENV GRADLE_USER_HOME=/opt/gradle-cache
WORKDIR /workspace

COPY offline-deps/gradle/gradle-8.6.tar.gz.part-* /tmp/
COPY offline-deps/gradle/gradle-cache.tar.gz.part-* /tmp/

RUN mkdir -p /opt \
 && cat $(ls /tmp/gradle-8.6.tar.gz.part-* | sort) > /tmp/gradle-8.6.tar.gz \
 && cat $(ls /tmp/gradle-cache.tar.gz.part-* | sort) > /tmp/gradle-cache.tar.gz \
 && tar -xzf /tmp/gradle-8.6.tar.gz -C /opt \
 && tar -xzf /tmp/gradle-cache.tar.gz -C /opt \
 && rm -f /tmp/gradle-8.6.tar.gz /tmp/gradle-cache.tar.gz /tmp/*.part-*

ENV PATH="/opt/gradle/gradle-8.6/bin:${PATH}"

FROM eclipse-temurin:17-jdk-jammy AS builder

ENV GRADLE_USER_HOME=/opt/gradle-cache
ENV PATH="/opt/gradle/gradle-8.6/bin:${PATH}"
WORKDIR /workspace

COPY --from=dependency-cache /opt/gradle /opt/gradle
COPY --from=dependency-cache /opt/gradle-cache /opt/gradle-cache
COPY app/ ./app/
COPY gatling-runner/ ./gatling-runner/
COPY security-runner/ ./security-runner/

# La migración de seguridad descarga la línea soportada Spring Boot 3.5 / Spring Cloud 2025.
RUN cd app && gradle --no-daemon clean test bootJar --stacktrace
# La primera construcción local descarga el plugin oficial, Gatling y Scala.
RUN gradle -p /workspace/gatling-runner --no-daemon dependencies --configuration gatlingRuntimeClasspath
# Prepara el plugin; la base NVD se descarga al ejecutar el primer análisis desde el dashboard.
RUN gradle -p /workspace/security-runner --no-daemon tasks --all

FROM eclipse-temurin:17-jdk-jammy AS runtime

ENV GRADLE_USER_HOME=/opt/gradle-cache \
    PATH="/opt/gradle/gradle-8.6/bin:${PATH}" \
    APP_DATA_ROOT=/app/data \
    RESULTS_DIR=/app/data/executions \
    PERFORMANCE_SIMULATIONS_DIR=/app/data/simulations \
    GATLING_COMMAND=/opt/gradle/gradle-8.6/bin/gradle \
    GATLING_PROJECT_DIR=/app/gatling-runner \
    SECURITY_PROJECT_DIR=/app/security-runner \
    SECURITY_SCAN_TARGET=/app/app.jar \
    SECURITY_REPORT_DIR=/app/data/security/reports \
    SECURITY_DATA_DIR=/app/data/security/database \
    API_PREFIX=/gatling/gatling-gen3-app/v0.1 \
    SERVER_SERVLET_CONTEXT_PATH=/gatling/gatling-gen3-app/v0.1 \
    HOME=/home/gatling \
    JAVA_TOOL_OPTIONS="-XX:+UseG1GC -Xms32m -Xmx384m -XX:MaxMetaspaceSize=192m"

RUN groupadd -g 1001 gatling && useradd -u 1001 -g gatling -m -d /home/gatling gatling
WORKDIR /app

COPY --from=builder /opt/gradle /opt/gradle
COPY --from=builder /opt/gradle-cache /opt/gradle-cache
COPY --from=builder /workspace/app/build/libs/app.jar /app/app.jar
COPY --from=builder /workspace/gatling-runner /app/gatling-runner
COPY --from=builder /workspace/security-runner /app/security-runner
COPY --from=trivy /usr/local/bin/trivy /usr/local/bin/trivy

RUN mkdir -p /app/data/simulations /app/data/executions /app/data/security/reports /app/data/security/database \
 && chown -R gatling:gatling /app /opt/gradle-cache /home/gatling

USER gatling
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM eclipse-temurin:17-jdk-jammy AS dependency-cache

ENV GRADLE_USER_HOME=/opt/gradle-cache
WORKDIR /workspace

COPY offline-deps/gradle/gradle-8.14.3.tar.gz.part-* /tmp/
COPY offline-deps/gradle/gradle-cache.tar.gz.part-* /tmp/

RUN mkdir -p /opt \
 && cat $(ls /tmp/gradle-8.14.3.tar.gz.part-* | sort) > /tmp/gradle-8.14.3.tar.gz \
 && cat $(ls /tmp/gradle-cache.tar.gz.part-* | sort) > /tmp/gradle-cache.tar.gz \
 && tar -xzf /tmp/gradle-8.14.3.tar.gz -C /opt \
 && tar -xzf /tmp/gradle-cache.tar.gz -C /opt \
 && test -x /opt/gradle/bin/gradle \
 && rm -f /tmp/gradle-8.14.3.tar.gz /tmp/gradle-cache.tar.gz /tmp/*.part-*

ENV PATH="/opt/gradle/bin:${PATH}"

FROM eclipse-temurin:17-jdk-jammy AS builder

ENV GRADLE_USER_HOME=/opt/gradle-cache
ENV PATH="/opt/gradle/bin:${PATH}"
WORKDIR /workspace

COPY --from=dependency-cache /opt/gradle /opt/gradle
COPY --from=dependency-cache /opt/gradle-cache /opt/gradle-cache
COPY app/ ./app/
COPY gatling-runner/ ./gatling-runner/

RUN gradle --version \
 && cd app \
 && gradle --offline --no-daemon clean test bootJar --stacktrace
RUN gradle -p /workspace/gatling-runner --offline --no-daemon dependencies --configuration gatlingRuntimeClasspath

FROM eclipse-temurin:17-jdk-jammy AS runtime

ENV GRADLE_USER_HOME=/opt/gradle-cache \
    PATH="/opt/gradle/bin:${PATH}" \
    APP_DATA_ROOT=/app/data \
    RESULTS_DIR=/app/data/executions \
    PERFORMANCE_SIMULATIONS_DIR=/app/data/simulations \
    GATLING_COMMAND=/opt/gradle/bin/gradle \
    GATLING_PROJECT_DIR=/app/gatling-runner \
    SECURITY_SCAN_ENABLED=false \
    API_PREFIX=/gatling/gatling-gen3-app/v0.1 \
    SERVER_SERVLET_CONTEXT_PATH=/gatling/gatling-gen3-app/v0.1 \
    HOME=/home/gatling 

RUN groupadd -g 1001 gatling && useradd -u 1001 -g gatling -m -d /home/gatling gatling
WORKDIR /app

COPY --from=builder /opt/gradle /opt/gradle
COPY --from=builder /opt/gradle-cache /opt/gradle-cache
COPY --from=builder /workspace/app/build/libs/app.jar /app/app.jar
COPY --from=builder /workspace/gatling-runner /app/gatling-runner

RUN mkdir -p /app/data/simulations /app/data/executions \
 && chown -R gatling:gatling /app /opt/gradle-cache /home/gatling

USER gatling
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

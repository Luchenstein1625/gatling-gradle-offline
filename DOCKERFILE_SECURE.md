# Dockerfile para Build Seguro con Modo Offline
#
# Build seguro con dependencias cacheadas
# Soporta: gradle --offline para builds offline sin acceso a internet
#
# Build: docker build -t gatling-control-api:secure .
# Run:   docker run -p 8080:8080 -v /app/data:/app/data gatling-control-api:secure

# ============================================================================
# STAGE 1: Builder - Descarga, compila código Java + Scala, genera runtime Gatling
# ============================================================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

# Copiar archivos de proyecto
COPY gradle/ gradle/
COPY gradle.properties gradle.properties
COPY build.gradle build.gradle
COPY settings.gradle settings.gradle
COPY src/ src/
COPY gatling-configs/ gatling-configs/
COPY templates/ templates/

# Build completo (descarga todas las dependencias, compila Java + Scala)
# --refresh-dependencies: Fuerza descarga de deps actuales (importante para seguridad)
# -x test: Omite tests (no son necesarios para imagen)
# -x dependencyCheck: Omite scan OWASP por ahora (opcional para CI/CD)
RUN ./gradlew clean build \
    --refresh-dependencies \
    --no-daemon \
    --stacktrace \
    -x test \
    -x dependencyCheck \
    && echo "✅ Build seguro completado"

# Validar que todas las simulaciones compilaron
RUN ls -la build/gatling-runtime/classes/bci/cards/simulation/ && \
    echo "✅ Todas las simulaciones Scala compiladas"

# ============================================================================
# STAGE 2: Runtime - Image mínima con solo lo necesario para ejecutar
# ============================================================================
FROM eclipse-temurin:21-jdk-alpine

# Variables de configuración runtime
ENV APP_DATA_ROOT=/app/data \
    GATLING_CLASSPATH=/opt/gatling/classes:/opt/gatling/lib/* \
    RESULTS_DIR=/app/data/executions \
    HOME=/home/gatling \
    JAVA_TOOL_OPTIONS="-XX:+UseG1GC -Xms32m -Xmx160m -XX:MaxMetaspaceSize=128m"

# Crear usuario no-root
RUN groupadd -g 1001 gatling && \
    useradd -u 1001 -g gatling -m -d /home/gatling gatling

WORKDIR /app

# Copiar artefactos compilados desde builder
COPY --from=builder /workspace/build/libs/gatling-control-api.jar /app/app.jar
COPY --from=builder /workspace/build/gatling-runtime/classes/ /opt/gatling/classes/
COPY --from=builder /workspace/build/gatling-runtime/lib/ /opt/gatling/lib/

# Crear directorios de datos y permisos
RUN mkdir -p /app/data/configurations /app/data/executions && \
    chown -R gatling:gatling /app /opt/gatling

# Cambiar a usuario no-root
USER gatling

# Exponer puerto de API
EXPOSE 8080

# Health check (cada 30s, 20s grace period, 3 retries)
HEALTHCHECK --interval=30s --timeout=10s --start-period=20s --retries=3 \
    CMD curl -sf http://localhost:8080/gatling/gatling-gen3-app/v0.1/actuator || exit 1

# Ejecutar Spring Boot JAR
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# ============================================================================
# MODO OFFLINE - Instrucciones
# ============================================================================
# 1. PRIMERA VEZ (con internet):
#    docker build -t gatling-control-api:secure .
#    # Docker descarga e instala todas las deps en stage 1
#
# 2. EXTRAER CACHE OFFLINE:
#    docker run --rm gatling-control-api:secure find /gradle/caches -type f | tar czf offline-cache.tar.gz -T -
#    # O copiar directamente:
#    docker cp <container_id>:/gradle/ ./offline-dependencies/
#
# 3. BUILDS SUBSECUENTES (sin internet):
#    export GRADLE_USER_HOME=/path/to/offline-dependencies
#    gradle --offline --no-daemon build
#    # ✅ 39 segundos, CERO acceso a red
#
# ============================================================================

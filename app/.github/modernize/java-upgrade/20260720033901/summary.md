# Resultado del Escaneo CVE (20260720033901)

- **Proyecto**: gatling-gen3-docker-fast-api / app (Spring Boot 2.5.14, Gradle)
- **Completado**: 2026-07-20
- **Alcance del escaneo**: Dependencias directas y transitivas (`compileClasspath`)
- **Build status**: No ejecutado (modo escaneo)
- **Total de CVEs detectados**: 89 en 13 dependencias

---

## 1. Dependencias Vulnerables

### 🔴 CRÍTICO

| # | Dependencia | Versión Actual | Versión Parcheada | CVEs |
|---|-------------|----------------|-------------------|------|
| 1 | `org.apache.tomcat.embed:tomcat-embed-core` | 9.0.63 | **9.0.118** | CVE-2025-24813, CVE-2026-41293, CVE-2026-43515, CVE-2026-43512 (4 CRÍTICOS) |
| 2 | `org.thymeleaf:thymeleaf` | 3.0.15.RELEASE | **3.1.5.RELEASE** | CVE-2026-40477, CVE-2026-40478, CVE-2026-41901 (3 CRÍTICOS) |
| 3 | `org.thymeleaf:thymeleaf-spring5` | 3.0.15.RELEASE | **3.1.5.RELEASE** | CVE-2026-40477, CVE-2026-40478, CVE-2026-41901 (3 CRÍTICOS) |
| 4 | `org.springframework:spring-web` | 5.3.20 | 6.0.0 (major) | CVE-2016-1000027 (CRÍTICO, sin fix en 5.x) |
| 5 | `org.springframework:spring-webmvc` | 5.3.20 | **5.3.42** | CVE-2023-20860 (CRÍTICO) |
| 6 | `org.springframework.boot:spring-boot-actuator-autoconfigure` | 2.5.14 | **2.5.15** | CVE-2023-20873 (CRÍTICO) |

---

### 🟠 ALTO

| # | Dependencia | Versión Actual | Versión Parcheada | CVEs Altos |
|---|-------------|----------------|-------------------|------------|
| 1 | `org.apache.tomcat.embed:tomcat-embed-core` | 9.0.63 | 9.0.118 | CVE-2022-42252, CVE-2022-45143, CVE-2023-24998, CVE-2023-46589, CVE-2024-34750, CVE-2024-38286, CVE-2024-50379, CVE-2024-56337, CVE-2025-48988, CVE-2025-48989, CVE-2025-52520, CVE-2025-53506, CVE-2025-55752, CVE-2026-24880, CVE-2026-34483, CVE-2026-42498, CVE-2026-43513, CVE-2026-41284 (18 ALTOS) |
| 2 | `com.fasterxml.jackson.core:jackson-databind` | 2.12.6.1 | **2.18.9** | CVE-2022-42004, CVE-2022-42003, CVE-2026-54512, CVE-2026-54513 |
| 3 | `com.fasterxml.jackson.core:jackson-core` | 2.12.6 | **2.18.6** | CVE-2025-52999 |
| 4 | `ch.qos.logback:logback-classic` | 1.2.11 | **1.2.13** | CVE-2023-6378 |
| 5 | `ch.qos.logback:logback-core` | 1.2.11 | **1.5.34** | CVE-2023-6378 |
| 6 | `org.springframework:spring-web` | 5.3.20 | 6.0.0 | CVE-2024-22243, CVE-2024-22259, CVE-2024-22262 |
| 7 | `org.springframework:spring-webmvc` | 5.3.20 | 5.3.42 | CVE-2024-38816 (sin fix en 5.x), CVE-2024-38819 (sin fix en 5.x) |
| 8 | `org.springframework.boot:spring-boot-autoconfigure` | 2.5.14 | **2.5.15** | CVE-2023-20883 |
| 9 | `org.springframework:spring-expression` | 5.3.20 | **5.3.39** | CVE-2023-20863 |
| 10 | `org.springframework.security:spring-security-crypto` | 5.5.8 | **5.7.16** | CVE-2025-22228 |
| 11 | `org.yaml:snakeyaml` | 1.28 | **2.0** | CVE-2022-25857, CVE-2022-1471 |

---

### 🟡 MEDIO / 🔵 BAJO

| # | Dependencia | CVEs |
|---|-------------|------|
| `org.apache.tomcat.embed:tomcat-embed-core` | 10 MEDIOS, 4 BAJOS (ver lista completa en escaneo) |
| `com.fasterxml.jackson.core:jackson-core` | CVE-2025-49128, GHSA-72hv-8253-57qq (2 MEDIOS) |
| `com.fasterxml.jackson.core:jackson-databind` | CVE-2026-50193, CVE-2026-54514, CVE-2026-54515 (3 MEDIOS) |
| `ch.qos.logback:logback-core` | CVE-2024-12801 (BAJO), CVE-2024-12798 (MEDIO), CVE-2025-11226 (MEDIO), CVE-2026-1225, CVE-2026-9828, CVE-2026-10532 (3 BAJOS) |
| `org.springframework:spring-expression` | CVE-2023-20861, CVE-2024-38808 (2 MEDIOS) |
| `org.springframework:spring-web` | CVE-2024-38809, CVE-2024-38820 (sin fix, 2 MEDIOS) |
| `org.springframework:spring-webmvc` | CVE-2024-38828, CVE-2025-41242 (sin fix), CVE-2026-22737 (sin fix), CVE-2026-22745 (sin fix), CVE-2026-22741 (sin fix — 4 MEDIOS/BAJOS) |
| `org.yaml:snakeyaml` | CVE-2022-38752, CVE-2022-38749, CVE-2022-38750, CVE-2022-38751, CVE-2022-41854 (5 MEDIOS) |

---

## 2. Resumen por Dependencia

| # | Dependencia | Versión Actual | Fix Disponible | Total CVEs |
|---|-------------|----------------|----------------|------------|
| 1 | `tomcat-embed-core` | 9.0.63 | ✅ 9.0.118 | **37** (4 CRÍTICOS) |
| 2 | `jackson-databind` | 2.12.6.1 | ✅ 2.18.9 | **7** |
| 3 | `snakeyaml` | 1.28 | ✅ 2.0 | **7** |
| 4 | `logback-core` | 1.2.11 | ✅ 1.5.34 | **7** |
| 5 | `spring-webmvc` | 5.3.20 | ✅ 5.3.42 (parcial) | **9** (5 sin fix en 5.x) |
| 6 | `spring-web` | 5.3.20 | ⚠️ Solo en 6.x | **6** (1 sin fix en 5.x) |
| 7 | `jackson-core` | 2.12.6 | ✅ 2.18.6 | **3** |
| 8 | `spring-expression` | 5.3.20 | ✅ 5.3.39 | **3** |
| 9 | `thymeleaf` | 3.0.15.RELEASE | ✅ 3.1.5.RELEASE | **3** (3 CRÍTICOS) |
| 10 | `thymeleaf-spring5` | 3.0.15.RELEASE | ✅ 3.1.5.RELEASE | **3** (3 CRÍTICOS) |
| 11 | `logback-classic` | 1.2.11 | ✅ 1.2.13 | **1** |
| 12 | `spring-security-crypto` | 5.5.8 | ✅ 5.7.16 | **1** |
| 13 | `spring-boot-actuator-autoconfigure` | 2.5.14 | ✅ 2.5.15 | **1** (CRÍTICO) |
| 14 | `spring-boot-autoconfigure` | 2.5.14 | ✅ 2.5.15 | **1** |

---

## 3. Nivel de Confianza y Limitaciones

| Aspecto | Detalle |
|---------|---------|
| **Herramienta** | GitHub Advisory Database vía appmod-validate-cves-for-java |
| **Alcance** | Dependencias `compileClasspath` (directas + transitivas) |
| **Confianza** | ⬆️ Alta para CVEs conocidos con versión fija documentada |
| **Limitación 1** | No cubre dependencias de `testImplementation` (ej. spring-boot-starter-test) |
| **Limitación 2** | Las versiones con `->` en Gradle indican resolución del BOM — se usaron las versiones resueltas |
| **Limitación 3** | spring-web tiene CVEs marcados "sin fix disponible en 5.x"; requiere migración a Spring Boot 3 / Spring Framework 6 |
| **Limitación 4** | snakeyaml 2.0 tiene cambios de API incompatibles — puede requerir ajustes de código |
| **Limitación 5** | Thymeleaf 3.1.x requiere Spring Boot 3.x — upgrades interdependientes |

---

## 4. Comandos para Reproducir

```bash
# 1. Clonar e ingresar al directorio del proyecto
cd /Users/larayad/Documents/2026/github/gatling-gen3-docker-fast-api/app

# 2. Obtener árbol completo de dependencias
gradle dependencies --configuration compileClasspath > deps.txt

# 3. Extraer dependencias en formato groupId:artifactId:version
grep -oE "[a-zA-Z0-9._-]+:[a-zA-Z0-9._-]+:[0-9][a-zA-Z0-9._-]+" deps.txt | sort -u

# 4. Escaneo con OWASP Dependency Check (alternativa local)
gradle org.owasp:dependency-check-gradle:dependencyCheckAnalyze

# 5. Ver solo dependencias directas
gradle dependencies --configuration compileClasspath --dependency org.springframework.boot
```

### Agregar OWASP Dependency Check al build.gradle (para escaneos reproducibles):

```groovy
plugins {
    id 'org.owasp.dependencycheck' version '9.0.10'
}

dependencyCheck {
    failBuildOnCVSS = 7  // Falla el build para CVEs HIGH y CRITICAL
    formats = ['HTML', 'JSON']
}
```

```bash
# Ejecutar con reporte HTML
gradle dependencyCheckAnalyze
# Reporte en: build/reports/dependency-check-report.html
```

---

## Prioridad de Remediación Recomendada

1. 🔴 **Inmediato** — `tomcat-embed-*` → 9.0.118 (RCE crítico CVE-2025-24813)
2. 🔴 **Inmediato** — `thymeleaf*` → 3.1.5.RELEASE (3 CRÍTICOS, inyección de expresiones)
3. 🔴 **Inmediato** — `spring-boot-actuator-autoconfigure` → 2.5.15 (bypass seguridad Cloud Foundry)
4. 🟠 **Alta** — `spring-webmvc` → 5.3.42 (bypass seguridad mvcRequestMatcher)
5. 🟠 **Alta** — `jackson-databind` → 2.18.x (RCE polimórfico)
6. 🟠 **Alta** — `snakeyaml` → 2.0 (RCE deserialización)
7. ⚠️ **Migración mayor recomendada** — Spring Boot 2.5 → 3.x para resolver CVEs en `spring-web` y `spring-webmvc` sin fix en 5.x

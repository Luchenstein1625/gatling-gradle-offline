# Corrección de vulnerabilidades críticas y altas

Fecha de revisión: 2026-07-20

## Alcance

La corrección se basa en los reportes `dependency-check-report.json` y
`trivy-report.json` entregados. No se agregaron supresiones de CVE ni se ocultaron
resultados.

## Dependencias de la aplicación corregidas

| Componente reportado | Versión anterior | Corrección aplicada |
|---|---:|---:|
| Spring Boot | 2.5.15 | 3.5.16 |
| Spring Framework | 5.3.x | administrado por Spring Boot 3.5.16 (línea 6.2.x) |
| Spring Security Crypto | 5.5.8 | administrado por Spring Boot 3.5.16 (línea 6.5.x) |
| Tomcat embebido | 9.0.118 | administrado por Spring Boot 3.5.16 (línea 10.1.x) |
| Spring Cloud | 2020.0.6 | 2025.0.3 |
| Thymeleaf | 3.0.15.RELEASE | 3.1.5.RELEASE |
| Thymeleaf para Spring | `thymeleaf-spring5` | `thymeleaf-spring6` 3.1.5.RELEASE |

También se eliminaron las versiones antiguas fijadas manualmente para Jackson,
SnakeYAML, Logback, Spring y Tomcat, de modo que el BOM compatible de Spring Boot
administre esas familias como un conjunto.

## Migración de código

Spring Boot 3 usa Jakarta EE. Se cambiaron las importaciones:

- `javax.servlet.http.HttpServletResponse` a `jakarta.servlet.http.HttpServletResponse`.
- `javax.annotation.PreDestroy` a `jakarta.annotation.PreDestroy`.

Se retiraron `spring-cloud-starter-bootstrap` y la versión explícita antigua de
`spring-vault-core`. La aplicación conserva `spring-cloud-starter-vault-config` y
la importación `optional:vault://` que ya están en el proyecto.

## Hallazgos que no pertenecen a `app.jar`

El comando Trivy actual analiza el contenedor completo. Por eso el reporte también
incluye librerías internas de Gradle, OWASP Dependency-Check, Gatling y del propio
binario Trivy. Esos hallazgos no deben confundirse con las dependencias de ejecución
de la aplicación.

El reporte entregado contiene además un hallazgo HIGH de `oras-go` dentro de Trivy
sin versión corregida publicada. No se suprimió: no es posible declararlo resuelto
hasta que el proyecto Trivy publique una versión que incorpore el parche.

## Verificación obligatoria

Desde la raíz del proyecto, con acceso a Maven Central:

```powershell
docker compose -f docker-compose-local.yml build --no-cache
docker compose -f docker-compose-local.yml up -d
```

Luego se debe ejecutar nuevamente **Análisis de vulnerabilidades** en el dashboard
y descargar el ZIP nuevo. La aceptación debe basarse en ese reporte generado sobre
la imagen reconstruida, no en el reporte anterior.

Para inspeccionar solamente el artefacto de la aplicación con Trivy:

```powershell
docker compose -f docker-compose-local.yml exec gatling-api sh -lc "trivy fs --scanners vuln --severity CRITICAL,HIGH /app/app.jar"
```

No se puede afirmar “cero vulnerabilidades” hasta completar esa reconstrucción y
el reanálisis. La compilación en el entorno de preparación quedó bloqueada antes de
descargar dependencias porque no tiene conectividad hacia Maven Central; no fue un
fallo de compilación Java.

# Actualización de seguridad v5

## Compatibilidad Vault preservada

- Spring Boot: `3.5.16`
- Spring Cloud BOM: `2025.0.3`
- Vault: `spring-cloud-starter-vault-config`, sin fijar una versión paralela
- Importación y autenticación Vault existentes: sin cambios

## Dependencias corregidas

- Tomcat Embed: `10.1.57` (core, EL y WebSocket alineados)
- Jackson Databind: `2.21.5`
- Apache HttpCore 5: `5.4.3` (core y H2 alineados)

El task `verifySecurityDependencyVersions` bloquea el `bootJar` si Gradle no
resuelve estas versiones o si falta el starter de Vault.

## Paso obligatorio antes de usarlo sin Internet

El ZIP no incluye el caché histórico porque contenía versiones antiguas y
dependencias ajenas al runtime. En un equipo conectado, desde PowerShell:

```powershell
.\scripts\regenerate-offline-cache.ps1
```

O desde Bash:

```bash
./scripts/regenerate-offline-cache.sh
```

El proceso:

1. crea un caché Gradle desde cero;
2. resuelve la aplicación, Gatling y OWASP actuales;
3. verifica las versiones de seguridad y la presencia de Vault;
4. reemplaza las partes offline solo cuando la generación tuvo éxito;
5. prueba una construcción final con `--network=none`.

## Validación final requerida

Después de regenerar el caché:

1. iniciar la aplicación en CERT;
2. confirmar la lectura real de secretos mediante AppRole;
3. ejecutar el análisis de seguridad;
4. comprobar que Trivy indique archivos Java detectados (no `num=0`);
5. revisar cualquier hallazgo restante antes de promover a PROD.

Trivy ahora extrae `app.jar` en un directorio temporal, analiza `BOOT-INF/lib`
en modo offline y elimina el temporal al terminar.

# Vulnerabilidades: ejecución exclusivamente local

Esta configuración deja en el repositorio únicamente `security-reports/latest-vulnerability-report.txt`.
El contenedor del servidor no contiene Trivy, OWASP Dependency-Check, `security-runner`, bases de CVE ni sus bibliotecas.

## Archivos que debes reemplazar o agregar

1. Reemplaza `Dockerfile`.
2. Reemplaza `Dockerfile.cache-refresh`.
3. Reemplaza o combina `.gitignore` conservando estas reglas.
4. Reemplaza o combina `.dockerignore` conservando estas reglas.
5. Agrega `scripts/run-local-vulnerability-scan.ps1`.
6. Crea `security-reports/` si todavía no existe.

No reemplaces `app/build.gradle`: conserva la versión consolidada con Bouncy Castle 1.84.

## Limpieza única antes de regenerar

Ejecuta desde PowerShell en la raíz del proyecto:

```powershell
Remove-Item .\security-runner -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item .\app\build, .\gatling-runner\build, .\.gradle, .\app\.gradle, .\gatling-runner\.gradle -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item .\security-reports\* -Force -ErrorAction SilentlyContinue
```

Esto elimina del proyecto la herramienta de análisis anterior y los artefactos generados. No elimina `offline-deps`.

## Regenerar el caché funcional offline

Ejecuta tu script actual:

```powershell
.\scripts\regenerate-offline-cache.ps1
```

El nuevo `Dockerfile.cache-refresh` solo incluirá dependencias de `app` y `gatling-runner`; no incorporará las del análisis de vulnerabilidades.

## Construir la imagen del servidor

```powershell
docker build --no-cache -t gatling-control-center:offline .
```

## Generar el único reporte local

```powershell
.\scripts\run-local-vulnerability-scan.ps1
```

Cada ejecución sobrescribe:

```text
security-reports/latest-vulnerability-report.txt
```

Trivy se ejecuta desde una imagen Docker local/externa y su base queda en el volumen Docker `trivy-cache`, fuera del repositorio y fuera de la imagen del servidor.

## Verificaciones antes de subir

```powershell
docker run --rm --entrypoint sh gatling-control-center:offline -c "command -v trivy || true; test ! -d /app/security-runner; find /app /opt/gradle-cache -type f -iname '*dependency-check*' -o -iname '*trivy*'"
```

El comando no debe listar Trivy, `security-runner` ni Dependency-Check.

```powershell
git status --short
git check-ignore -v .\security-runner\build.gradle
git check-ignore -v .\security-reports\reporte-antiguo.txt
git check-ignore -v .\security-reports\latest-vulnerability-report.txt
```

Los dos primeros archivos de prueba deben estar ignorados. `latest-vulnerability-report.txt` no debe estar ignorado.

Luego agrega solo los cambios válidos:

```powershell
git add Dockerfile Dockerfile.cache-refresh .gitignore .dockerignore scripts/run-local-vulnerability-scan.ps1 security-reports/latest-vulnerability-report.txt
git status --short
```

Revisa que `git status` no incluya `security-runner`, JAR, directorios `build`, bases de CVE ni reportes anteriores antes de confirmar el commit.

## Nota sobre el bloqueo en la aplicación

La imagen establece `SECURITY_SCAN_ENABLED=false` y no contiene ningún ejecutable ni proyecto capaz de efectuar el análisis. Si el dashboard todavía muestra un botón de vulnerabilidades, el backend debe hacer que su controlador/servicio respete esa propiedad o debe eliminarse esa pantalla. Para realizar esa última corrección en código se necesitan las clases Java actuales del módulo de seguridad; no estaban entre los archivos recibidos.

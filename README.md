# Gatling Control API

Proyecto estable:

```text
Java 21 + Spring Boot + Gradle + Scala + Gatling
```

## Funciones

- Interfaz web.
- Swagger similar a FastAPI `/docs`.
- Operación por `curl`.
- Plantillas PEAK/TPS.
- Login dinámico o token externo.
- Subida YAML y CSV.
- Ejecución asíncrona.
- Estado.
- Log en vivo SSE.
- Log descargable.
- Reporte ZIP.
- Recursos JVM/cgroups/pod.
- Logs del propio pod con RBAC.
- Clasificación de DNS, TLS, conexión, heap y aserciones.
- Build y dependencias offline.

## Primera preparación

Requisitos:

- Windows PowerShell.
- Internet durante la preparación.
- Docker Desktop iniciado.

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\01_prepare_online.ps1
```

Este comando:

1. Descarga Java 21 portable.
2. Descarga Gradle 8.6 y valida SHA-256.
3. Descarga Spring Boot, Springdoc, Gatling, Scala y dependencias.
4. Compila.
5. Repite la compilación con `--offline`.
6. Ejecuta OWASP Dependency-Check.
7. Construye Docker con `--network=none`.
8. Exporta la imagen final.

## Ejecutar local

```powershell
.\scripts\02_run_local.ps1
```

Abrir:

```text
Interfaz: http://localhost:8080
Swagger:  http://localhost:8080/docs
OpenAPI:  http://localhost:8080/openapi
```

## Docker Compose: Local vs Servidor

### Configuración Local (con límites de memoria)

Para desarrollo/testing: Simula el ambiente de servidor con límites de recursos.

**Opción 1: Usar script**
```bash
./scripts/compose-local.sh up -d
./scripts/compose-local.sh logs -f
./scripts/compose-local.sh down
```

**Opción 2: Docker Compose directo**
```bash
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d
docker compose -f docker-compose.yml -f docker-compose.local.yml logs -f
docker compose -f docker-compose.yml -f docker-compose.local.yml down
```

**Opción 3: Usando .env.local (recomendado)**
```bash
# Cargar configuración local automáticamente
export $(cat .env.local)
docker compose up -d
docker compose logs -f
docker compose down
```

Características:
- Límite de memoria: **768 MB** (simula servidor real)
- Reserva de memoria: **384 MB**
- API limitada a: `-Xmx160m`
- Gatling usa: ~50% del cgroup (≈384 MB)

### Configuración Servidor (sin límites)

Para producción/deploy: Sin límites de recursos (orquestador maneja límites).

**Opción 1: Script**
```bash
./scripts/compose-prod.sh up -d
./scripts/compose-prod.sh logs -f
./scripts/compose-prod.sh down
```

**Opción 2: Docker Compose directo**
```bash
docker compose -f docker-compose.yml up -d
docker compose logs -f
docker compose down
```

Características:
- Sin límites de memoria (usa lo disponible en host)
- Ideal para Kubernetes/orquestadores que manejan sus propios límites
- Configuración base reutilizable en cualquier ambiente

### Variables de Entorno

Todos los ambientes usan las mismas variables de entorno:

```bash
API_PREFIX=/gatling/gatling-gen3-app/v0.1
JAVA_TOOL_OPTIONS="-XX:+UseG1GC -Xms32m -Xmx160m -XX:MaxMetaspaceSize=128m"

# Credenciales BCI (desde env)
BCI_LOGIN_URL=${BCI_LOGIN_URL:-}
BCI_LOGIN_BASIC_AUTH=${BCI_LOGIN_BASIC_AUTH:-}
BCI_API_BASE_URL=${BCI_API_BASE_URL:-}
BCI_API_TOKEN=${BCI_API_TOKEN:-}
```

### Notas sobre Archivos

- **`docker-compose.yml`** (git): Configuración base para servidor (sin límites)
- **`docker-compose.local.yml`** (.gitignore): Agrega límites de memoria para local
- **`.env.local`** (.gitignore): Activador de docker-compose.local.yml
- **`scripts/compose-local.sh`**: Wrapper ejecutable para desarrollo
- **`scripts/compose-prod.sh`**: Wrapper ejecutable para producción

## Flujo por curl

Descargar plantilla:

```bash
curl -o peak-login.yaml http://localhost:8080/api/templates/peak-login
```

Subir YAML y CSV:

```bash
curl -X POST http://localhost:8080/api/configurations \
  -F "configuration=@peak-login.yaml" \
  -F "users=@usuarios.csv"
```

Ejecutar:

```bash
curl -X POST http://localhost:8080/api/executions/CONFIGURATION_ID
```

Estado:

```bash
curl http://localhost:8080/api/executions/EXECUTION_ID
```

Log en vivo:

```bash
curl -N http://localhost:8080/api/executions/EXECUTION_ID/logs/stream
```

Reporte:

```bash
curl -o reporte.zip http://localhost:8080/api/executions/EXECUTION_ID/report
```

Recursos:

```bash
curl http://localhost:8080/api/system/resources
```

## Secretos

No guardar tokens, contraseñas, Basic Auth ni cookies en YAML.

Local:

```powershell
$env:BCI_LOGIN_URL="https://..."
$env:BCI_LOGIN_BASIC_AUTH="..."
$env:BCI_API_BASE_URL="https://..."
$env:BCI_API_TOKEN="..."
docker compose up -d
```

Kubernetes:

```yaml
valueFrom:
  secretKeyRef:
    name: gatling-api-secrets
    key: api-token
```

## Seguridad

Las versiones están fijadas y el build incorpora OWASP Dependency-Check.
No es posible garantizar que una dependencia permanezca sin vulnerabilidades
futuras. La promoción debe exigir:

- reporte Dependency-Check sin HIGH/CRITICAL;
- escaneo de la imagen con la herramienta corporativa;
- imagen base aprobada y fijada por digest;
- revisión de secretos;
- RBAC mínimo.

## Advertencia de memoria

La API y Gatling se ejecutan como procesos Java separados dentro del mismo
contenedor. Con límite de 768 MiB se limita la API a `-Xmx160m` y Gatling usa
aproximadamente 50% del cgroup.

Para cargas más exigentes, la arquitectura recomendada en Kubernetes es que la
API cree un Job Gatling separado. Esta versión no crea Jobs para evitar requerir
permisos adicionales.


## Corrección v1.0.1

Se reemplazó la versión inexistente `Spring Boot 3.4.15` por:

```text
Spring Boot:             3.5.16
springdoc OpenAPI:       2.8.17
Dependency Management:  1.1.7
Gatling Plugin:          3.15.1.1
OWASP Dependency-Check:  12.2.2
```

Springdoc 2.8.x es la línea compatible con Spring Boot 3.5.x.

También se corrigió la ejecución asíncrona: la API usa un executor de un solo
hilo, por lo que acepta la solicitud, responde con `QUEUED` y ejecuta solamente
una prueba Gatling a la vez para proteger memoria y CPU.


## Corrección v1.0.2

Se corrigió el error de compilación:

```text
overloaded method feed ... cannot be applied to (Object)
```

La causa era mezclar dos tipos distintos en el feeder: `csv(...).circular` e
`Iterator.continually(...)`. Ahora `users.csv` es obligatorio para ambas
modalidades porque las operaciones requieren `rutLogin`, `cuenta` y `tarjeta`;
LOGIN utiliza además `claveLogin`.

Descargar plantilla CSV:

```bash
curl -o users-template.csv http://localhost:8080/api/templates/users-csv
```

Columnas obligatorias:

```text
rutLogin,claveLogin,cuenta,tarjeta
```


## Flujo definitivo y rápido

### Preparar y construir

```powershell
.\scripts\01_prepare_online.ps1
```

Este script ya no ejecuta OWASP Dependency-Check. Descarga dependencias,
compila, valida modo offline y construye la imagen Docker.

### Ejecutar local

```powershell
.\scripts\02_run_local.ps1
```

### Escaneo de seguridad opcional

```powershell
.\scripts\04_security_scan.ps1
```

Con NVD API Key:

```powershell
$env:NVD_API_KEY="TU_API_KEY"
.\scripts\04_security_scan.ps1
```

El escaneo queda separado para no bloquear builds urgentes.


## Archivos versionados para operación offline

El `.gitignore` conserva en el repositorio:

```text
offline/java/
offline/gradle/
offline/gradle-cache/
```

Estos directorios son necesarios para compilar sin Internet.

Se excluyen únicamente:

```text
build/
.gradle/
reports/
logs/
data/executions/
data/configurations/
offline/docker-images/
reportes de Dependency-Check
heap dumps y temporales
```

La imagen Docker exportada puede distribuirse por un repositorio de artefactos,
ACR o almacenamiento corporativo, sin agregar archivos TAR pesados a Git.


## Pruebas de todos los endpoints

Con la API levantada:

```powershell
.\scripts\05_test_all_endpoints.ps1
```

El script valida:

- health;
- OpenAPI;
- recursos del contenedor;
- descarga de todas las plantillas;
- subida de una configuración;
- inicio de una ejecución;
- consulta de estado;
- descarga del log;
- descarga del reporte cuando la prueba finaliza correctamente.

Los archivos individuales están en:

```text
tests/http/
```

Pueden abrirse con IntelliJ HTTP Client o la extensión REST Client de VS Code.

## Mocks públicos incluidos

Se incluyen dos plantillas de carga mínima:

```text
mock-jsonplaceholder.yaml
mock-httpbin.yaml
```

JSONPlaceholder es una API REST falsa para pruebas y prototipos. HTTPBin es un
servicio de request/response para probar verbos, headers y estados HTTP.

Estas plantillas usan solamente un usuario y diez segundos. No deben utilizarse
para pruebas de carga o estrés contra servicios públicos. Para carga real, use
un mock interno controlado por el equipo.


## CSV obligatorio solo cuando corresponde

La API ahora lee `simulationClass` antes de guardar la configuración.

```text
ConfigurableBcimsSimulation → users.csv obligatorio
PublicMockSimulation        → users.csv opcional
```

Por lo tanto:

- `mock-jsonplaceholder.yaml`: subir solo el YAML.
- `mock-httpbin.yaml`: subir solo el YAML.
- pruebas BCIMS: subir YAML + `users.csv`.

La respuesta de `POST /api/configurations` incluye:

```json
{
  "configurationId": "cfg-...",
  "simulationClass": "bci.cards.simulation.PublicMockSimulation",
  "usersCsvRequired": "false",
  "status": "VALIDATED"
}
```


## Corrección Netty/Gatling

Se corrigió:

```text
NoClassDefFoundError: io/netty/channel/IoHandle
```

La causa era que `io.spring.dependency-management` aplicaba el BOM de Spring
Boot globalmente y reemplazaba las versiones de Netty requeridas por Gatling.

La solución mantiene Gradle y las mismas versiones, pero aplica el BOM de
Spring únicamente a las dependencias de la API:

```groovy
implementation platform(
    "org.springframework.boot:spring-boot-dependencies:3.5.16"
)
```

`gatlingRuntimeClasspath` conserva ahora las versiones transitivas compatibles
de Gatling. El build ejecuta `verifyGatlingNetty` y falla antes de construir
Docker si falta `io.netty.channel.IoHandle`.

No se elimina `offline/gradle-cache`; se reutiliza y Gradle descarga solamente
los artefactos faltantes o corregidos.

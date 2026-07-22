# Gatling Control Center — ejecución local y despliegue en clúster

Aplicación Spring Boot para cargar, validar y ejecutar simulaciones Gatling Scala, consultar Vault y descargar resultados.

## 1. Flujo recomendado

El orden correcto es:

1. En un equipo local **con Internet**, descargar imágenes y resolver las dependencias.
2. Validar localmente la construcción y la ejecución.
3. Generar los artefactos necesarios para un servidor sin Internet.
4. Construir la imagen final, publicarla en el registro accesible por AKS y desplegarla.
5. Validar Vault y ejecutar las pruebas desde el dashboard del ambiente.

> `docker compose` se utiliza para construir y probar localmente. El pod de AKS se despliega con Kubernetes; no se levanta ejecutando Compose dentro del clúster.

## 2. Requisitos

### Equipo local

- Git.
- Docker Desktop o Docker Engine activo.
- Docker Compose v2.
- Internet solamente durante la preparación inicial o al regenerar dependencias.
- PowerShell en Windows, o Bash en Linux/macOS.

Validación:

```bash
docker version
docker compose version
```

### Servidor o equipo de despliegue

- Docker y Docker Compose para construir la imagen, si corresponde.
- Acceso al registro de contenedores de BCI.
- `kubectl` configurado para el namespace `bci-api`.
- Imágenes base ya cargadas si el servidor no tiene Internet.
- Secretos de Kubernetes y truststore administrados mediante el procedimiento autorizado del banco.

## 3. Preparación inicial en local con Internet

Clonar y entrar al proyecto:

```bash
git clone https://github.com/Luchenstein1625/gatling-gradle-offline.git
cd gatling-gradle-offline
```

Descargar las imágenes base:

```bash
docker pull eclipse-temurin:17-jdk-jammy
docker pull aquasec/trivy:0.72.0
```

El repositorio ya contiene las partes de Gradle y del caché en `offline-deps/gradle/`. Si se modificaron versiones o dependencias, hay que regenerarlas conectado a Internet.

### Windows PowerShell

```powershell
.\scripts\regenerate-offline-cache.ps1
```

### Linux o macOS

```bash
chmod +x scripts/regenerate-offline-cache.sh
./scripts/regenerate-offline-cache.sh
```

Ese proceso actualiza el caché y valida el `Dockerfile` sin red. No se debe regenerar si no cambiaron Gradle, Gatling, Scala o las dependencias Java.

## 4. Construir y levantar el contenedor local

Desde la raíz del proyecto:

```bash
docker compose build --no-cache
docker compose up -d
docker compose ps
docker compose logs -f gatling-api
```

Antes de construir, verificar que el contexto Docker incluya estas rutas requeridas por el Dockerfile:

- `app/`
- `gatling-runner/`
- `offline-deps/gradle/gradle-8.14.3.tar.gz.part-*`
- `offline-deps/gradle/gradle-cache.tar.gz.part-*`

Si `gatling-runner/` o `offline-deps/` están excluidos en `.dockerignore`, el build puede fallar con errores tipo `COPY ... not found`.

Cuando el contenedor esté `healthy`, abrir:

- Menú: <http://localhost:8080/gatling/gatling-gen3-app/v0.1/>
- Vault: <http://localhost:8080/gatling/gatling-gen3-app/v0.1/dashboard>
- Pruebas: <http://localhost:8080/gatling/gatling-gen3-app/v0.1/performance>
- Health: <http://localhost:8080/gatling/gatling-gen3-app/v0.1/health>

Detener sin borrar `./data`:

```bash
docker compose down
```

## 5. Vault en local

### 5.1 Modo local seguro, sin Vault real

Es el modo predeterminado de `docker-compose.yml`. Vault queda deshabilitado y se utiliza solamente el valor ficticio `LOCAL_MOCK_ONLY`.

```bash
docker compose up -d
```

Validar:

```bash
curl http://localhost:8080/gatling/gatling-gen3-app/v0.1/vault-test
```

El estado esperado es `LOCAL_MOCK` o `DISABLED`. Esto valida la aplicación, no la conectividad con Vault.

### 5.2 Probar Vault real desde local

Solo funciona desde una red que pueda resolver y alcanzar `vault-server-service.bci-infra:8200`, y si se dispone del truststore y AppRole autorizados.

No guardar `VAULT_ROLE_ID`, `VAULT_SECRET_ID`, tokens ni contraseñas en el repositorio, README, archivos `.env` versionados o capturas.

Configurar las variables en la sesión antes de iniciar Compose. Ejemplo PowerShell:

```powershell
$env:VAULT_ENABLED="true"
$env:SPRING_CLOUD_VAULT_ENABLED="true"
$env:VAULT_FAIL_FAST="true"
$env:VAULT_SCHEME="https"
$env:VAULT_HOST="vault-server-service.bci-infra"
$env:VAULT_PORT="8200"
$env:VAULT_AUTHENTICATION="APPROLE"
$env:VAULT_ROLE_ID="<ROLE_ID_AUTORIZADO>"
$env:VAULT_SECRET_ID="<SECRET_ID_AUTORIZADO>"
$env:VAULT_SECRET_KEY_NAME="token"
$env:VAULT_TRUST_STORE="file:/ruta/dentro/del/contenedor/pubcerts.ts"
$env:VAULT_TRUST_STORE_PWD="<PASSWORD_AUTORIZADO>"

docker compose up -d --force-recreate
docker compose logs -f gatling-api
```

El truststore debe estar montado dentro del contenedor. El Compose actual solo monta `./data:/app/data`; si se prueba Vault real localmente, se debe agregar un volumen aprobado o copiar el truststore a una ruta montada y configurar `VAULT_TRUST_STORE` con la ruta **interna** correspondiente.

Comprobaciones:

```bash
curl http://localhost:8080/gatling/gatling-gen3-app/v0.1/vault-test/precheck
curl http://localhost:8080/gatling/gatling-gen3-app/v0.1/vault-test
```

Estados principales:

- `SUCCESS`: la clave configurada fue cargada.
- `NOT_FOUND`: se llegó a Vault, pero no se encontró la clave/ruta esperada.
- `ERROR` o `NOT_READY`: revisar DNS, puerto, TLS, AppRole, permisos, backend y ruta.

La configuración actual consulta el backend `secret`, aplicación `token_pruebas` y perfil `qa`, equivalente a `secret/token_pruebas/qa` según la configuración de Spring Cloud Vault.

## 6. Ejecutar una prueba en local

1. Abrir `/performance`.
2. Seleccionar un `.scala` desde `mock-simulations/`.
3. Presionar **Validar**.
4. Presionar **Guardar simulación**.
5. En la lista, presionar **Ejecutar**.
6. Seguir los logs hasta `SUCCEEDED` o `FAILED`.
7. Presionar **Descargar resultados**.

Las simulaciones aceptadas deben declarar exactamente:

```scala
package bci.cards.simulation
```

Los datos persistentes quedan en:

```text
data/simulations/
data/executions/
```

API equivalente:

```bash
# Validar
curl -F "file=@mock-simulations/PublicJsonPlaceholderSmokeSimulation.scala" \
  http://localhost:8080/gatling/gatling-gen3-app/v0.1/api/performance/simulations/validate

# Guardar
curl -F "file=@mock-simulations/PublicJsonPlaceholderSmokeSimulation.scala" \
  http://localhost:8080/gatling/gatling-gen3-app/v0.1/api/performance/simulations

# Listar
curl http://localhost:8080/gatling/gatling-gen3-app/v0.1/api/performance/simulations

# Ejecutar
curl -X POST \
  -d "simulationClass=bci.cards.simulation.PublicJsonPlaceholderSmokeSimulation" \
  http://localhost:8080/gatling/gatling-gen3-app/v0.1/api/performance/executions
```

La respuesta de ejecución entrega un `id`. Usarlo para consultar estado, logs y resultados:

```bash
curl http://localhost:8080/gatling/gatling-gen3-app/v0.1/api/performance/executions/<ID>
curl "http://localhost:8080/gatling/gatling-gen3-app/v0.1/api/performance/executions/<ID>/logs?offset=0"
curl -o gatling-results.zip http://localhost:8080/gatling/gatling-gen3-app/v0.1/api/performance/executions/<ID>/results
```

## 7. Preparar el paquete para un servidor sin Internet

En el equipo conectado, exportar las imágenes:

```bash
chmod +x scripts/export-offline-images.sh
./scripts/export-offline-images.sh
```

Se genera:

```text
offline-image-bundle.tgz
```

Transferir al servidor:

- el repositorio completo;
- `offline-image-bundle.tgz`;
- `offline-deps/gradle/*.part-*`.

En el servidor, importar y construir:

```bash
chmod +x scripts/import-offline-images.sh scripts/offline-build.sh
./scripts/import-offline-images.sh offline-image-bundle.tgz
docker compose build --no-cache
```

Nota: al transferir el repositorio al servidor, incluir también la carpeta `gatling-runner/` junto con `app/` y `offline-deps/`.

También se puede usar el wrapper:

```bash
./scripts/offline-build.sh offline-image-bundle.tgz
```

Validación estricta recomendada antes de entregar al administrador:

```bash
docker compose build --no-cache --network none
```

El comando operativo del administrador puede seguir siendo:

```bash
docker compose build
docker compose up -d
```

Eso funcionará sin Internet únicamente cuando las imágenes base estén cargadas y `offline-deps/` esté completo.

### Checklist pre-admin (antes de subir al cluster)

1. Verificar que existen las partes offline:

```bash
ls -lh offline-deps/gradle/gradle-8.14.3.tar.gz.part-*
ls -lh offline-deps/gradle/gradle-cache.tar.gz.part-*
```

2. Verificar que el contexto incluye carpetas requeridas por Dockerfile:

```bash
test -d app && echo "OK app"
test -d gatling-runner && echo "OK gatling-runner"
```

3. Validar build estricto sin red:

```bash
docker build --no-cache --network none -f Dockerfile -t gatling-offline-verify .
```

4. Validar build por Compose (flujo operativo):

```bash
docker compose build
```

5. Solo si ambos comandos anteriores terminan en `BUILD SUCCESSFUL`/`Successfully built`, continuar con el push y despliegue en Kubernetes.

## 8. Publicar la imagen para AKS

El clúster debe descargar la imagen desde un registro que pueda alcanzar. Ejemplo con el ACR definido actualmente en `k8s/deployment.yaml`:

```bash
docker compose build
docker tag gatling-gradle-offline-gatling-api:latest \
  bcirg3crtrgandes01acr001.azurecr.io/gatling/gatling-gen3-docker-fast-api:<VERSION>
docker push bcirg3crtrgandes01acr001.azurecr.io/gatling/gatling-gen3-docker-fast-api:<VERSION>
```

El nombre local puede variar según el directorio. Confirmarlo con:

```bash
docker compose images
```

Antes de desplegar, actualizar en `k8s/deployment.yaml` la propiedad `image:` con la misma `<VERSION>` publicada. No usar `latest` y no reconstruir una imagen distinta para promoverla entre CERT y PROD; promover la misma imagen por digest.

## 9. Preparar Vault en Kubernetes

El manifiesto espera:

- Secret `vault-role-secret-id`, con claves `VAULT_ROLE_ID` y `VAULT_SECRET_ID`.
- Secret `ms-secret`, montado en `/vol-ms`, que debe contener `pubcerts.ts`.

Comprobar solamente existencia y claves, sin imprimir valores:

```bash
kubectl -n bci-api get secret vault-role-secret-id
kubectl -n bci-api get secret ms-secret
kubectl -n bci-api describe secret vault-role-secret-id
kubectl -n bci-api describe secret ms-secret
```

Los secretos deben ser creados o sincronizados por el administrador mediante el mecanismo autorizado de BCI. Evitar pasar valores sensibles directamente en comandos compartidos o almacenados en el historial de shell.

## 10. Desplegar en el clúster

Aplicar el manifiesto:

```bash
kubectl apply -f k8s/deployment.yaml
kubectl -n bci-api rollout status deployment/ms-vault-checker-deployment
kubectl -n bci-api get pods -l app=ms-vault-checker
kubectl -n bci-api logs -f deployment/ms-vault-checker-deployment
```

Si se publicó una versión nueva pero el manifiesto no cambió:

```bash
kubectl -n bci-api rollout restart deployment/ms-vault-checker-deployment
kubectl -n bci-api rollout status deployment/ms-vault-checker-deployment
```

## 11. Validar Vault y ejecutar pruebas en el servidor

Dashboard CERT:

<https://bci-api-crt004.bci.cl/gatling/gatling-gen3-app/v0.1/dashboard>

Rutas del ambiente:

- Menú: `https://bci-api-crt004.bci.cl/gatling/gatling-gen3-app/v0.1/`
- Vault: `https://bci-api-crt004.bci.cl/gatling/gatling-gen3-app/v0.1/dashboard`
- Pruebas: `https://bci-api-crt004.bci.cl/gatling/gatling-gen3-app/v0.1/performance`
- Health: `https://bci-api-crt004.bci.cl/gatling/gatling-gen3-app/v0.1/health`

Validar Vault desde la interfaz o mediante:

```bash
curl -sS https://bci-api-crt004.bci.cl/gatling/gatling-gen3-app/v0.1/vault-test/precheck
curl -sS https://bci-api-crt004.bci.cl/gatling/gatling-gen3-app/v0.1/vault-test
```

Para ejecutar pruebas en el servidor, abrir `/performance` y repetir el mismo flujo local: validar, guardar, ejecutar, revisar logs y descargar resultados.

Si se usa API, reemplazar `http://localhost:8080` en los comandos de la sección 6 por:

```text
https://bci-api-crt004.bci.cl
```

## 12. Diagnóstico rápido

### El build intenta conectarse a Internet

- Confirmar que existen las imágenes con `docker image inspect`.
- Confirmar las partes en `offline-deps/gradle/`.
- Ejecutar `docker compose build --no-cache --network none` para identificar el paso que aún depende de red.

### El pod no inicia

```bash
kubectl -n bci-api describe pod -l app=ms-vault-checker
kubectl -n bci-api logs deployment/ms-vault-checker-deployment --previous
```

Revisar especialmente `ImagePullBackOff`, secretos ausentes, permisos del volumen, truststore y límites de memoria.

### Vault aparece `NOT_READY`

Revisar en `/vault-test/precheck`:

- resolución DNS;
- conexión TCP a 8200;
- existencia de `/vol-ms/pubcerts.ts`;
- bean de Vault;
- AppRole y permisos para `secret/token_pruebas/qa`;
- existencia de la clave indicada por `VAULT_SECRET_KEY_NAME`.

### Gatling falla por conectividad

`UnknownHostException`, `ConnectException` o timeout normalmente indican DNS, ruta de red, proxy o firewall hacia la API objetivo. No significan necesariamente que la simulación Scala esté mal.

### Recursos del pod

El manifiesto actual usa:

```text
requests: cpu 100m, memoria 384Mi
limits:   cpu 1, memoria 768Mi
Java:     Xms32m, Xmx160m, MaxMetaspaceSize=128m
```

## 13. Seguridad

- No versionar Role ID, Secret ID, tokens, cookies, certificados privados ni claves reales.
- No usar datos productivos en simulaciones locales.
- No imprimir secretos en logs ni respuestas HTTP.
- Los resultados y cachés generados bajo `data/` no deben subirse al repositorio.
- CERT y PROD deben utilizar la misma imagen promovida por digest.

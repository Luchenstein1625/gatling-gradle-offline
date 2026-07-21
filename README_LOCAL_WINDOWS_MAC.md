# Ejecución local — Windows y macOS

Esta guía permite levantar Gatling Control Center en un computador personal sin conectarse al Vault, las APIs ni la red interna del banco.

El modo local utiliza exclusivamente:

- `VAULT_ENABLED=false`;
- una credencial ficticia `LOCAL_MOCK_ONLY`;
- las simulaciones públicas incluidas en `mock-simulations/`.

No copies tokens, certificados, Role ID, Secret ID, URLs internas ni datos reales del banco al computador personal.

## 1. Requisitos

### Windows

- Windows 10 u 11 de 64 bits.
- Docker Desktop.
- WSL 2 habilitado para Docker Desktop.
- PowerShell 5.1 o superior.
- Al menos 4 GB de memoria disponible para Docker.

### macOS

- macOS con procesador Intel o Apple Silicon.
- Docker Desktop.
- Terminal con `zsh` o `bash`.
- Al menos 4 GB de memoria disponible para Docker.

Verifica Docker:

```text
docker version
docker compose version
```

Si alguno falla, inicia Docker Desktop y espera hasta que indique que el motor está ejecutándose.

## 2. Abrir el proyecto

Descomprime el proyecto y abre una terminal en la carpeta que contiene:

```text
Dockerfile
docker-compose.yml
app/
offline-deps/
mock-simulations/
```

Ejemplo Windows:

```powershell
cd "C:\ruta\gatling-gradle-offline"
```

Ejemplo macOS:

```bash
cd "/ruta/gatling-gradle-offline"
```

## 3. Configuración local sin Vault

El `docker-compose.yml` ya contiene valores locales seguros como predeterminados. Puedes levantarlo directamente.

Si prefieres declararlos explícitamente, usa los siguientes comandos.

### Windows PowerShell

```powershell
$env:SPRING_PROFILES_ACTIVE="local"
$env:VAULT_ENABLED="false"
$env:SPRING_CLOUD_VAULT_ENABLED="false"
$env:VAULT_SECRET_KEY_NAME="BCI_LOGIN_BASIC_AUTH"
$env:BCI_LOGIN_BASIC_AUTH="LOCAL_MOCK_ONLY"
$env:GATLING_COMMAND="/opt/gradle/gradle-8.14.3/bin/gradle"
```

### macOS Terminal

```bash
export SPRING_PROFILES_ACTIVE="local"
export VAULT_ENABLED="false"
export SPRING_CLOUD_VAULT_ENABLED="false"
export VAULT_SECRET_KEY_NAME="BCI_LOGIN_BASIC_AUTH"
export BCI_LOGIN_BASIC_AUTH="LOCAL_MOCK_ONLY"
export GATLING_COMMAND="/opt/gradle/gradle-8.14.3/bin/gradle"
```

`LOCAL_MOCK_ONLY` no es una credencial real. Las simulaciones públicas comprueban que la variable exista, pero no la envían en headers, query params ni cuerpos HTTP.

## 4. Construir la imagen

La primera construcción puede tardar porque restaura Gradle y su caché desde `offline-deps/`.

Windows o macOS:

```text
docker compose build --no-cache
```

Si termina correctamente, levanta la aplicación:

```text
docker compose up
```

Para ejecutarla en segundo plano:

```text
docker compose up -d
```

Para ver los logs:

```text
docker compose logs -f gatling-api
```

## 5. Abrir la aplicación

Menú principal:

```text
http://localhost:8080/gatling/gatling-gen3-app/v0.1/
```

Dashboard Vault:

```text
http://localhost:8080/gatling/gatling-gen3-app/v0.1/dashboard
```

Carga y ejecución Gatling:

```text
http://localhost:8080/gatling/gatling-gen3-app/v0.1/performance
```

En modo local es normal que el dashboard Vault informe que Vault está deshabilitado. El módulo de rendimiento debe indicar que la clave local `BCI_LOGIN_BASIC_AUTH` está cargada.

## 6. Probar la carga de simulaciones

En `/performance`:

1. Presiona **Seleccionar archivo**.
2. Selecciona `mock-simulations/PublicJsonPlaceholderSmokeSimulation.scala`.
3. Presiona **Validar**.
4. Confirma que aparezca `Archivo válido`.
5. Presiona **Guardar simulación**.
6. Revisa la ruta mostrada:

```text
bci/cards/simulation/PublicJsonPlaceholderSmokeSimulation.scala
```

Dentro del contenedor se almacena en:

```text
/app/data/simulations/bci/cards/simulation/PublicJsonPlaceholderSmokeSimulation.scala
```

## 7. Comprobar el runtime Gatling

La parte superior de `/performance` muestra:

```text
Gatling: Disponible
```

o:

```text
Gatling: No encontrado
```

Para ejecutar, la imagen debe contener:

```text
/opt/gradle/gradle-8.14.3/bin/gradle
```

Compruébalo con:

```text
docker compose exec gatling-api sh -lc "ls -l /opt/gradle/gradle-8.14.3/bin/gradle && test -f /app/gatling-runner/build.gradle"
```

La imagen local incorpora Gradle y prepara el runner oficial de Gatling para Scala. La primera construcción requiere Internet para descargar el plugin y sus dependencias. Si cambiaste el Dockerfile o venías de una imagen anterior, reconstruye sin caché.

## 8. Ejecutar un mockup

Si Gatling aparece disponible:

1. Presiona **Ejecutar** junto a la simulación.
2. Confirma la ejecución.
3. Observa la terminal.
4. Espera el estado `SUCCEEDED` o `FAILED`.
5. Presiona **Descargar resultados**.

El ZIP descargado contiene el log de ejecución y los archivos generados por Gatling.

Los mockups realizan un usuario y una solicitud. No los conviertas en pruebas de carga contra servicios públicos.

## 9. Si el pod o contenedor no tiene Internet

Las simulaciones públicas necesitan salida HTTPS. Si la red bloquea Internet, la terminal puede mostrar:

```text
UnknownHostException
ConnectException
Connection timed out
```

Eso puede indicar una restricción de red y no necesariamente un error del cargador.

## 10. Detener la aplicación

Si se está ejecutando en primer plano, presiona `Ctrl + C`.

Para detener contenedores en segundo plano:

```text
docker compose down
```

Los archivos ubicados en `./data` se mantienen en el computador porque Docker Compose monta:

```text
./data:/app/data
```

## 11. Reconstruir después de modificar Java o HTML

```text
docker compose down
docker compose build --no-cache
docker compose up
```

Luego abre el navegador con una recarga completa:

- Windows: `Ctrl + F5`.
- macOS: `Command + Shift + R`.

## 12. Problemas frecuentes

### Docker Desktop no está iniciado

Síntomas:

```text
Cannot connect to the Docker daemon
error during connect
dockerDesktopLinuxEngine
```

Solución: inicia Docker Desktop y espera a que el motor quede disponible.

### Puerto 8080 ocupado

Modifica temporalmente `docker-compose.yml`:

```yaml
ports:
  - "8081:8080"
```

Después abre:

```text
http://localhost:8081/gatling/gatling-gen3-app/v0.1/
```

### El dashboard conserva una versión anterior

Reconstruye sin caché:

```text
docker compose build --no-cache
docker compose up --force-recreate
```

### Vault intenta conectarse localmente

Comprueba las variables dentro del contenedor:

```text
docker compose exec gatling-api sh -lc "env | sort | grep VAULT"
```

Debe aparecer:

```text
VAULT_ENABLED=false
SPRING_CLOUD_VAULT_ENABLED=false
```

### La clave local figura como no cargada

Comprueba:

```text
docker compose exec gatling-api sh -lc 'test -n "$BCI_LOGIN_BASIC_AUTH" && echo CONFIGURADA || echo VACIA'
```

No imprimas el contenido de una credencial real. En local debe utilizarse únicamente `LOCAL_MOCK_ONLY`.

## 13. Diferencia entre local y banco

| Configuración | Local | Banco |
|---|---|---|
| Vault | Deshabilitado | Habilitado |
| Credencial | `LOCAL_MOCK_ONLY` | Obtenida desde Vault |
| APIs | Públicas de prueba | Internas autorizadas |
| Perfil | `local` | `qa` u otro definido |
| Gatling | Runtime local de la imagen | Runtime de la imagen corporativa |

Nunca copies valores reales del entorno bancario al modo local.

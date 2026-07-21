# Versión alineada al patrón Vault recibido

Esta entrega utiliza el patrón observado en el ejemplo bancario compartido:

- `spring.config.import: vault://`
- `spring.cloud.vault.application-name`
- `spring.profiles.active`
- autenticación `APPROLE` porque así está definido el Deployment de esta aplicación
- lectura del secreto desde el `Environment` de Spring

No se fuerza KV v1 ni KV v2 desde Java y no se agregaron dependencias a `build.gradle`.

## Ruta esperada

Con:

- backend predeterminado: `secret`
- application name: `token_pruebas`
- perfil activo: `qa`

Spring intentará cargar el contexto correspondiente a:

`secret/token_pruebas/qa`

## Nombre de la clave

El nombre de la clave interna se configura en el Deployment:

`VAULT_SECRET_KEY_NAME=token`

Si el administrador confirma otro nombre, se cambia únicamente esa variable. No se debe registrar ni compartir el valor del secreto.

## Dependencias

No se agregó ni actualizó ninguna librería. Las versiones existentes del proyecto se conservaron para no romper el build offline ni introducir dependencias no aprobadas.

## Carga y ejecución de simulaciones

Las clases cargadas deben declarar:

`package bci.cards.simulation`

Una clase como `BcimsInformacionBasicaPeakSimulation` se almacena en:

`/app/data/simulations/bci/cards/simulation/BcimsInformacionBasicaPeakSimulation.scala`

El servicio valida extensión, tamaño, package, coincidencia entre nombre de archivo y clase, imports Gatling, uso de `BCI_LOGIN_BASIC_AUTH`, patrones peligrosos evidentes y presencia de `setUp`/`assertions`.

El valor obtenido desde Vault se entrega al proceso Gatling mediante `BCI_LOGIN_BASIC_AUTH`. No se modifica el archivo Scala y el valor se redacta si aparece literalmente en la salida del proceso.

### Runtime requerido

La imagen debe incluir un launcher Gatling ejecutable en:

`/opt/gatling/bin/gatling.sh`

La ruta puede modificarse con `GATLING_COMMAND`. La página `/performance` informa si el ejecutable y la clave de Vault están disponibles.

### Endpoints

- `POST /api/performance/simulations/validate`
- `POST /api/performance/simulations`
- `GET /api/performance/simulations`
- `GET /api/performance/runtime`
- `POST /api/performance/executions`
- `GET /api/performance/executions/{id}`
- `GET /api/performance/executions/{id}/logs`
- `GET /api/performance/executions/{id}/results`

La ejecución de código Scala cargado debe permanecer restringida a la red y controles de acceso corporativos. La validación de contenido reduce riesgos evidentes, pero no reemplaza autenticación, autorización, revisión de código ni aislamiento del pod.

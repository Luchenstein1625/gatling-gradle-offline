# Validaciones para integrar Vault y desplegar Gatling Control Center

## Objetivo

Validar que la aplicación pueda:

1. Iniciar correctamente en el clúster.
2. Autenticarse en HashiCorp Vault mediante AppRole.
3. Cargar el secreto asociado a `token_pruebas` en el contexto de Spring.
4. Utilizar la propiedad recuperada en las pruebas de rendimiento sin exponer su valor.
5. Ejecutar Gatling y generar logs y reportes descargables.

> No se deben compartir por correo, chat, capturas ni repositorio los valores de Role ID, Secret ID, tokens, contraseñas o secretos. Solo se deben confirmar nombres, rutas lógicas, archivos y mecanismos de inyección.

---

## Preguntas para el IL

### 1. Nombre del secreto y propiedad disponible

- ¿`token_pruebas` debe configurarse exactamente como `spring.cloud.vault.application-name`?
- ¿Cuál es el nombre exacto de la propiedad que Spring deja disponible después de cargar el secreto?
- ¿La propiedad que debe consumir Gatling es `token`, `BCI_LOGIN_BASIC_AUTH` u otro nombre?
- ¿El nombre de la propiedad distingue mayúsculas y minúsculas?
- ¿La propiedad debe consumirse directamente con `@Value`, mediante `Environment` o utilizando alguna clase/configuración corporativa?

Ejemplo de confirmación esperada, sin mostrar el valor:

```text
Application name: token_pruebas
Propiedad disponible en Spring: BCI_LOGIN_BASIC_AUTH
Forma recomendada de consumo: @Value / Environment / componente corporativo
```

### 2. Perfil del ambiente

- ¿Cuál es el perfil Spring correcto del ambiente donde se desplegará: `qa`, `crt` u otro?
- ¿El perfil debe declararse con `SPRING_PROFILES_ACTIVE`?
- ¿El nombre del ambiente interviene en la resolución del secreto?
- ¿Existe alguna diferencia entre el perfil local y el utilizado en el clúster?

### 3. `application.yml` y `bootstrap.yml`

- Para las versiones de Spring Boot y Spring Cloud utilizadas por este proyecto, ¿la configuración de Vault debe inicializarse desde `application.yml`?
- ¿Este proyecto necesita `bootstrap.yml` o la inicialización actual mediante `spring.config.import` es suficiente?
- Si se requiere `bootstrap.yml`, ¿qué propiedades deben ir allí y cuáles deben permanecer en `application.yml`?
- ¿La imagen base o alguna dependencia corporativa ya habilita el contexto bootstrap automáticamente?
- ¿Existe un proyecto vigente con las mismas versiones de Spring que se pueda usar como referencia para esta decisión?

### 4. Dependencias y patrón corporativo

- ¿Las versiones de Spring Boot, Spring Cloud y Spring Vault del `build.gradle` son compatibles con el patrón vigente del banco?
- ¿Debe utilizarse alguna dependencia o BOM corporativo adicional?
- ¿Existe una librería interna que realice la integración y que deba reemplazar parte de la configuración actual?
- ¿La aplicación debe fallar al iniciar cuando Vault no está disponible en el clúster?
- ¿El comportamiento local con Vault deshabilitado está correctamente separado del comportamiento del clúster?

### 5. Consumo seguro en las pruebas

- ¿Es correcto entregar la propiedad recuperada al proceso Gatling mediante una variable de entorno del proceso hijo?
- ¿Existe un mecanismo corporativo preferido para entregar credenciales a Gatling?
- ¿Qué nombre de variable deben leer las simulaciones Scala?
- ¿Está permitido registrar únicamente que la propiedad fue cargada, sin mostrar su contenido?
- ¿El enmascaramiento actual del log cumple con el estándar del banco?

### 6. Criterio funcional esperado

- ¿Qué resultado se considera evidencia suficiente de una lectura correcta?
- ¿Es correcto validar solamente presencia, nombre de propiedad y cantidad de valores, sin mostrar el secreto?
- ¿Qué mensaje debería mostrar el dashboard cuando la credencial esté disponible?
- ¿Qué comportamiento se espera cuando la propiedad no exista o Vault no esté disponible?

### Archivos para revisar con el IL

- `app/build.gradle`
- `app/src/main/resources/application.yml`
- `app/src/main/resources/bootstrap.yml`, solamente si se determina que corresponde
- `app/src/main/java/cl/bci/vaultchecker/controller/VaultDashboardController.java`
- `app/src/main/java/cl/bci/vaultchecker/performance/PerformanceExecutionService.java`
- Una simulación Scala de ejemplo que consuma la propiedad

---

## Preguntas para el ingeniero Cloud

### 1. Deployment y ambiente

- ¿El namespace configurado es el correcto para esta aplicación?
- ¿El deployment utiliza la imagen y etiqueta nuevas que contienen Gradle, Gatling, OWASP Dependency-Check y Trivy?
- ¿El clúster está descargando realmente la imagen nueva o está reutilizando una imagen anterior?
- ¿Conviene utilizar una etiqueta de imagen inmutable en lugar de reutilizar `latest`?
- ¿El perfil Spring configurado en el pod coincide con el ambiente?

### 2. AppRole y Kubernetes Secret

- ¿Existe el Kubernetes Secret `vault-role-secret-id` en el namespace del pod?
- ¿Contiene las claves llamadas exactamente `VAULT_ROLE_ID` y `VAULT_SECRET_ID`?
- ¿El pod tiene permisos para referenciar ese Kubernetes Secret?
- ¿Las variables aparecen definidas dentro del contenedor sin imprimir sus valores?
- ¿El AppRole está asociado a esta aplicación y ambiente?
- ¿La autorización permite que la aplicación lea el secreto asociado a `token_pruebas`?

Comprobación segura esperada:

```text
VAULT_ROLE_ID: definida
VAULT_SECRET_ID: definida
```

No se deben imprimir sus contenidos.

### 3. Conectividad hacia Vault

- ¿El DNS `vault-server-service.bci-infra` resuelve desde el pod?
- ¿Existe conectividad TCP desde el pod al puerto `8200`?
- ¿Hay NetworkPolicy, firewall o service mesh que pueda bloquear la comunicación?
- ¿El esquema correcto es HTTPS?
- ¿La aplicación necesita proxy, sidecar o configuración adicional para salir desde el namespace?

Validaciones sugeridas desde el pod:

```bash
getent hosts vault-server-service.bci-infra
```

```bash
nc -vz vault-server-service.bci-infra 8200
```

Estas comprobaciones validan DNS y conectividad; no muestran credenciales.

### 4. Certificado y TrustStore

- ¿El Kubernetes Secret `ms-secret` existe en el namespace?
- ¿El volumen se monta correctamente en `/vol-ms`?
- ¿El archivo esperado existe como `/vol-ms/pubcerts.ts`?
- ¿El usuario no-root `1001` puede leer el archivo?
- ¿La contraseña del TrustStore se inyecta desde una fuente segura?
- ¿El certificado o cadena de confianza corresponde al endpoint actual de Vault?

Validaciones sugeridas:

```bash
ls -l /vol-ms/pubcerts.ts
```

```bash
test -r /vol-ms/pubcerts.ts && echo TRUSTSTORE_READABLE
```

### 5. Variables de ejecución

Confirmar que el pod tenga las rutas nuevas:

```text
GATLING_COMMAND=/opt/gradle/gradle-8.6/bin/gradle
GATLING_PROJECT_DIR=/app/gatling-runner
SECURITY_GRADLE_COMMAND=/opt/gradle/gradle-8.6/bin/gradle
SECURITY_PROJECT_DIR=/app/security-runner
TRIVY_COMMAND=/usr/local/bin/trivy
```

Validaciones sugeridas:

```bash
test -x /opt/gradle/gradle-8.6/bin/gradle && echo GRADLE_OK
test -f /app/gatling-runner/build.gradle && echo GATLING_RUNNER_OK
test -f /app/security-runner/build.gradle && echo SECURITY_RUNNER_OK
test -x /usr/local/bin/trivy && echo TRIVY_OK
```

### 6. Volúmenes y persistencia

- ¿`/app/data` debe ser persistente entre reinicios del pod?
- ¿Qué StorageClass y tamaño corresponden para resultados Gatling y bases de vulnerabilidades?
- ¿El usuario `1001` tiene permisos de escritura sobre `/app/data`?
- ¿Los reportes deben conservarse después de reemplazar el pod?
- ¿Existe una política de retención y eliminación de reportes?

Directorios utilizados:

```text
/app/data/simulations
/app/data/executions
/app/data/security/reports
/app/data/security/database
/app/data/security/trivy-cache
```

### 7. Recursos del pod

- ¿Los límites actuales soportan simultáneamente Spring Boot, compilación Scala, Gatling y los analizadores de seguridad?
- ¿Conviene ejecutar los análisis de seguridad en un Job separado?
- ¿El pod ha presentado eventos `OOMKilled`, throttling de CPU o fallos de almacenamiento?
- ¿Se puede aumentar temporalmente memoria y CPU durante el análisis inicial?

Los analizadores pueden requerir más memoria que la API durante la descarga y procesamiento inicial de sus bases.

### 8. Acceso a Internet y repositorios

- ¿El pod puede acceder a las fuentes requeridas para actualizar las bases de vulnerabilidades?
- ¿Debe utilizarse un proxy corporativo?
- ¿Las dependencias deben descargarse desde Nexus/Artifactory en lugar de repositorios públicos?
- ¿Existe una clave NVD corporativa o un mirror interno autorizado?
- ¿La actualización debe realizarse en el pod, durante el pipeline o mediante un Job programado?

---

## Evidencias que se deben solicitar

Sin revelar secretos, solicitar:

- Nombre de la imagen y etiqueta desplegada.
- ID o digest de la imagen ejecutada por el pod.
- Perfil Spring activo.
- Confirmación de que Role ID y Secret ID están definidos.
- Resultado de DNS y conexión TCP.
- Confirmación de lectura del TrustStore.
- Estado del pod y eventos recientes.
- Resultado de los endpoints de diagnóstico con valores enmascarados.
- Confirmación del nombre exacto de la propiedad cargada desde Vault.

---

## Criterios para considerar la integración lista

- El pod inicia sin errores de autenticación ni certificados.
- DNS y conexión TCP hacia Vault funcionan.
- AppRole se encuentra inyectado desde Kubernetes Secret.
- El TrustStore existe y es legible por el usuario del contenedor.
- Spring carga la propiedad esperada sin mostrar su contenido.
- El dashboard distingue entre una lectura real y el modo mock local.
- Gatling recibe la propiedad únicamente durante la ejecución.
- Los logs no contienen credenciales.
- Los resultados Gatling se descargan con `execution.log` y el reporte HTML.
- La imagen desplegada contiene los ejecutables y runners configurados.

---

## Resumen breve para enviar por chat

### Al IL

> ¿Me ayudas a confirmar si `token_pruebas` debe ir como `spring.cloud.vault.application-name`, cuál es el nombre exacto de la propiedad que queda disponible en Spring y cuál es el perfil correcto del ambiente? También necesito confirmar, para nuestras versiones de Spring, si corresponde usar solo `application.yml`, `spring.config.import` o además `bootstrap.yml`. No necesito valores sensibles.

### Al ingeniero Cloud

> ¿Me ayudas a validar que el pod esté usando la imagen nueva, que las variables de AppRole estén definidas desde Kubernetes Secret, que exista conectividad a `vault-server-service.bci-infra:8200`, que `/vol-ms/pubcerts.ts` sea legible y que las rutas de Gradle, Gatling y Trivy existan dentro del contenedor? No necesitamos imprimir ninguna credencial.

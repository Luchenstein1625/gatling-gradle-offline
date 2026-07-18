# 🔒 Plan de Remediación de Vulnerabilidades
## Proyecto: Gatling Control API (Spring Boot + Gatling + Gradle + Docker)

> Última actualización: 2026
>
> Objetivo: eliminar vulnerabilidades detectadas por Veracode/SCA manteniendo compatibilidad con Gatling y ejecución Offline.

---

# 1. Cómo identificar quién utiliza una librería

Antes de actualizar cualquier dependencia SIEMPRE ejecutar:

```bash
gradlew dependencyInsight --dependency NOMBRE_LIBRERIA --configuration runtimeClasspath
```

Ejemplos:

```bash
gradlew dependencyInsight --dependency spring-core
gradlew dependencyInsight --dependency netty-codec-http
gradlew dependencyInsight --dependency httpclient5
gradlew dependencyInsight --dependency commons-compress
gradlew dependencyInsight --dependency pebble
```

También es recomendable generar el árbol completo:

```bash
gradlew dependencies > dependencies.txt
```

o

```bash
gradlew dependencies --configuration runtimeClasspath
```

---

# 2. Vulnerabilidades encontradas

---

# Spring Framework

## spring-core

Versiones detectadas

```
spring-core-6.2.1.jar
spring-core-6.2.2.jar
spring-web-6.2.1.jar
```

Buscar

```bash
gradlew dependencyInsight --dependency spring-core
gradlew dependencyInsight --dependency spring-web
```

Buscar en código

```
build.gradle

gradle.properties

libs.versions.toml

dependencyManagement
```

Actualizar

Spring Boot administra esta dependencia.

Nunca actualizar spring-core manualmente.

Actualizar únicamente:

```
Spring Boot
```

---

# Apache HttpClient

Versiones detectadas

```
httpclient5-5.4.1.jar
```

Buscar

```bash
gradlew dependencyInsight --dependency httpclient5
```

Actualizar

Puede venir desde

- Spring
- Apache HTTP Client
- SDK externos

---

# Commons Compress

Versión

```
commons-compress-1.25.0.jar
```

Buscar

```bash
gradlew dependencyInsight --dependency commons-compress
```

Puede provenir de

- Maven
- Gradle
- Spring
- Apache Commons

---

# Commons Lang

Detectado

```
commons-lang
commons-lang3
```

Buscar

```bash
gradlew dependencyInsight --dependency commons-lang

gradlew dependencyInsight --dependency commons-lang3
```

---

# Pebble

Detectado

```
pebble-3.2.1
pebble-3.2.2
pebble-3.2.3
```

Buscar

```bash
gradlew dependencyInsight --dependency pebble
```

Normalmente viene desde

```
springdoc
Swagger
Templates
```

---

# Netty

Detectado

```
netty-codec-http

netty-codec-http2

netty-codec

netty-handler
```

Buscar

```bash
gradlew dependencyInsight --dependency netty-codec

gradlew dependencyInsight --dependency netty-codec-http

gradlew dependencyInsight --dependency netty-codec-http2

gradlew dependencyInsight --dependency netty-handler
```

Muy importante

NO actualizar Netty manualmente.

Debe mantenerse compatible con

- Gatling
- Reactor Netty
- Spring Boot

---

# Tomcat

Detectado

```
tomcat-embed-core

tomcat-juli
```

Buscar

```bash
gradlew dependencyInsight --dependency tomcat-embed-core

gradlew dependencyInsight --dependency tomcat-juli
```

Actualizar

Solo actualizando Spring Boot.

---

# Jackson

Detectado

```
jackson-core

jackson-databind
```

Buscar

```bash
gradlew dependencyInsight --dependency jackson-core

gradlew dependencyInsight --dependency jackson-databind
```

---

# JSON Smart

Detectado

```
json-smart
```

Buscar

```bash
gradlew dependencyInsight --dependency json-smart
```

Generalmente viene desde

```
Nimbus JWT

OAuth

OpenAPI
```

---

# Logback

Detectado

```
logback-core

logback-classic
```

Buscar

```bash
gradlew dependencyInsight --dependency logback-core

gradlew dependencyInsight --dependency logback-classic
```

---

# Log4j

Detectado

```
log4j-core
```

Buscar

```bash
gradlew dependencyInsight --dependency log4j-core
```

Si aparece

Eliminar completamente.

El proyecto utiliza Logback.

---

# Protobuf

Detectado

```
protobuf-java
```

Buscar

```bash
gradlew dependencyInsight --dependency protobuf-java
```

---

# Bouncy Castle

Detectado

```
bcprov-jdk18on
```

Buscar

```bash
gradlew dependencyInsight --dependency bcprov
```

---

# JGit

Detectado

```
org.eclipse.jgit
```

Buscar

```bash
gradlew dependencyInsight --dependency jgit
```

---

# Plexus

Detectado

```
plexus-utils

plexus-archiver
```

Buscar

```bash
gradlew dependencyInsight --dependency plexus-utils
```

## IMPORTANTE

En las capturas Veracode aparece

```
apache-maven-3.9.14/lib/
```

Eso significa que Veracode está escaneando TODO Maven.

No es una dependencia del proyecto.

Debe excluirse del ZIP.

---

# Maven Shared Utils

Detectado

```
maven-shared-utils
```

Buscar

```bash
gradlew dependencyInsight --dependency maven-shared-utils
```

También corresponde al Maven Offline.

No debería viajar al ZIP del proyecto.

---

# Apache AirCompressor

Detectado

```
aircompressor
```

Buscar

```bash
gradlew dependencyInsight --dependency aircompressor
```

---

# H11

Detectado

```
h11
```

Corresponde al antiguo proyecto Python.

No debería existir en este proyecto Java.

---

# 3. Vulnerabilidades SAST

---

## Command Injection

Buscar

```
ProcessBuilder

Runtime.getRuntime()

exec(

ProcessBuilder.start()

ProcessBuilder.command()
```

Buscar en IntelliJ

```
Ctrl+Shift+F

ProcessBuilder

Runtime.getRuntime

exec(
```

---

## Path Traversal

Buscar

```
Files.copy

Files.move

Paths.get

new File

MultipartFile.transferTo
```

Verificar

```
normalize()

startsWith()

Path.of()
```

---

## Local File Inclusion

Buscar

```
ResourceLoader

FileSystemResource

InputStreamResource

FileInputStream
```

---

## Improper Input Validation

Buscar

```
@RequestParam

@PathVariable

MultipartFile

@RequestBody
```

Verificar

```
@NotNull

@Pattern

@Validated

@Valid
```

---

## HTTP Request Smuggling

Relacionado con

```
Tomcat

Netty

Spring Web
```

Normalmente desaparece actualizando Spring Boot.

---

## Information Disclosure

Buscar

```
printStackTrace()

Exception.toString()

stacktrace

log.error(e)

ResponseEntity(e.getMessage())
```

---

# 4. Qué NO subir a Veracode

Eliminar del ZIP

```
.git/

offline/

offline-dependencies/

gradle-cache/

maven-repository/

apache-maven/

gradle/

jdk/

binaries/

reports/

logs/

results/

build-cache/

.idea/

.vscode/

.gradle/
```

Solo subir

```
src/

build.gradle

settings.gradle

gradle.properties

Dockerfile

docker-compose.yml

scripts/

README.md
```

o el

```
bootJar
```

---

# 5. Checklist de remediación

| Estado | Acción |
|---------|--------|
|⬜|Actualizar Spring Boot|
|⬜|Actualizar Spring Security|
|⬜|Actualizar SpringDoc|
|⬜|Actualizar Gatling|
|⬜|Revisar Netty|
|⬜|Eliminar Log4J|
|⬜|Actualizar HttpClient5|
|⬜|Actualizar Commons Compress|
|⬜|Actualizar Commons Lang|
|⬜|Actualizar Pebble|
|⬜|Actualizar Protobuf|
|⬜|Actualizar Bouncy Castle|
|⬜|Actualizar Jackson|
|⬜|Eliminar Maven Offline del ZIP|
|⬜|Eliminar Gradle Cache del ZIP|
|⬜|Eliminar repositorio Maven Offline|
|⬜|Revisar ProcessBuilder|
|⬜|Revisar Path Traversal|
|⬜|Revisar File Upload|
|⬜|Revisar Logging|
|⬜|Volver a ejecutar Veracode|

---

# 6. Comandos útiles

Árbol completo

```bash
gradlew dependencies
```

Dependencias runtime

```bash
gradlew dependencies --configuration runtimeClasspath
```

Dependencias Gatling

```bash
gradlew dependencies --configuration gatlingRuntimeClasspath
```

Buscar dependencia

```bash
gradlew dependencyInsight --dependency NOMBRE
```

OWASP

```bash
gradlew dependencyCheckAnalyze
```

Actualizar dependencias

```bash
gradlew --refresh-dependencies
```

Limpiar cache

```bash
gradlew clean
```

Build

```bash
gradlew build
```

Build Offline

```bash
gradlew --offline build
```
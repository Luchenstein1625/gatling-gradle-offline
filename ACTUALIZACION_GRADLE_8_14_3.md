# Actualización a Gradle 8.14.3

El proyecto fue migrado de Gradle 8.6 a Gradle 8.14.3 para retirar las
bibliotecas internas antiguas incluidas en la distribución 8.6.

## Componentes actualizados

- Dockerfiles principal, local y de regeneración del caché.
- Docker Compose local y de servidor.
- Configuración Spring y valores predeterminados de los servicios Java.
- Despliegue Kubernetes.
- Scripts de construcción y regeneración offline.
- Documentación y comandos operativos.
- Distribución real de Gradle 8.14.3 dividida en partes de 50 MB.

## Regeneración obligatoria del caché

El caché debe regenerarse para que todas las dependencias de `app`,
`gatling-runner` y `security-runner` queden resueltas con la configuración
vigente:

```powershell
.\scripts\regenerate-offline-cache.ps1
```

El script construye el caché usando conexión, reemplaza los paquetes solo
cuando la exportación termina correctamente y finalmente comprueba el build
con `--network=none`.

## Construcción final

```powershell
docker build --network=none --no-cache `
  -t gatling-control-center:offline `
  .
```

## Verificaciones

```powershell
docker run --rm --entrypoint sh gatling-control-center:offline `
  -c "/opt/gradle/gradle-8.14.3/bin/gradle --version"
```

```powershell
docker run --rm `
  -v /var/run/docker.sock:/var/run/docker.sock `
  -v trivy-cache:/root/.cache/trivy `
  aquasec/trivy:latest image --scanners vuln gatling-control-center:offline
```

El resultado de Trivy debe revisarse después de construir la imagen final; la
actualización de Gradle no implica por sí sola que todos los componentes de la
imagen queden sin hallazgos.

# Vault checker

## Flujo rápido

- `Spring Boot` arranca con `SPRING_PROFILES_ACTIVE=qa`.
- `Spring Cloud Vault` usa `APPROLE` contra `vault-server-service.bci-infra:8200`.
- El secret se busca en `secret/token_pruebas/qa`.
- El valor leído se expone en el dashboard y en `/vault-test`.

## Señales de validación

- `SUCCESS`: el secreto fue leído.
- `NOT_FOUND`: la ruta o el backend no coincidieron.
- `ERROR`: falló la autenticación o la conexión.

## Build offline

- La imagen y el build están preparados para ejecutarse sin acceso a Internet.
- El paquete offline debe contener `eclipse-temurin:17-jdk-jammy`,
  `aquasec/trivy:0.72.0`, Gradle 8.14.3 y el caché Gradle vigente.
- En un equipo conectado, exporta las imágenes con
  `./scripts/export-offline-images.sh`.
- En el servidor sin Internet, ejecuta
  `./scripts/offline-build.sh offline-image-bundle.tgz`.
- CERT y PROD deben promover la misma imagen por digest; no se debe reconstruir
  una imagen distinta para PROD.

Los resultados de Gatling y las bases/cachés de OWASP y Trivy se generan en
`data/` y no se versionan. El control OWASP falla ante hallazgos con CVSS 7.0 o
superior.

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

- La imagen y el build están preparados para ejecutarse sin acceso a internet.
- El build local validado usa `docker compose build --no-cache`.
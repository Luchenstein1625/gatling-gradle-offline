# Simulaciones públicas de humo

Estos archivos sirven únicamente para probar el flujo de carga, validación, ejecución, terminal y descarga del Gatling Control Center.

Todas las simulaciones:

- usan `package bci.cards.simulation`;
- ejecutan un solo usuario;
- realizan una sola solicitud;
- tienen duración máxima de 15 segundos;
- no envían el secreto de Vault a Internet;
- no deben modificarse para realizar carga contra los servicios públicos.

## Orden sugerido

1. `PublicJsonPlaceholderSmokeSimulation.scala`
2. `PublicPostmanEchoSmokeSimulation.scala`
3. `PublicJsonPlaceholderPostSmokeSimulation.scala`

JSONPlaceholder es una API falsa para pruebas y prototipos. Postman Echo está diseñado para probar clientes HTTP y solicitudes de ejemplo.

Si el pod corporativo no tiene salida a Internet, la ejecución fallará por DNS, timeout o conectividad; eso no implica necesariamente un error del cargador o de Gatling.

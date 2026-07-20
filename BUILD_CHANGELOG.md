# Build Changelog

- Vault bootstrap validado contra secret/token_pruebas/qa.
- Dashboard muestra version de build para detectar despliegues desactualizados.
- Endpoint /vault-test/read entrega source y token_fingerprint.
- Endpoint /vault-test/token-pruebas permite leer el secret token_pruebas directamente.
- /vault-test responde el token cargado por Spring con valor enmascarado en la version nueva.
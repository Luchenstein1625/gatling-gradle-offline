# Pruebas manuales de la API

1. Abrir Swagger:
http://localhost:8080/docs

2. Verificar Health:
GET /actuator/health

3. Descargar una plantilla:
GET /api/templates/{template}

4. Subir configuración:
POST /api/configurations
- configuration = peak-login.yaml
- users = users.csv (solo pruebas BCI)

5. Ejecutar:
POST /api/executions/{configurationId}

6. Consultar estado:
GET /api/executions/{executionId}

7. Ver log:
GET /api/executions/{executionId}/logs

8. Descargar reporte:
GET /api/executions/{executionId}/report

9. Recursos del servidor:
GET /api/system/resources

10. Logs del Pod (solo Kubernetes con permisos):
GET /api/kubernetes/pod/logs

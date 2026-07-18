# 📋 Guía API - Generación de Pruebas Gatling

## Tabla de Contenidos
1. [Inicio Rápido](#inicio-rápido)
2. [Formato YAML](#formato-yaml)
3. [Formato CSV](#formato-csv)
4. [Crear Nueva Prueba](#crear-nueva-prueba)
5. [Ejemplos Completos](#ejemplos-completos)
6. [Endpoints API](#endpoints-api)
7. [Archivos Modificables](#archivos-modificables)
8. [Solución de Problemas](#solución-de-problemas)

---

## Inicio Rápido

### Requisitos
- Docker ejecutando: `docker run -d --name gatling-test -p 8080:8080 gatling-control-api:latest`
- API disponible en: `http://localhost:8080/gatling/gatling-gen3-app/v0.1`
- Archivos YAML + CSV preparados

### Paso 1: Crear archivos de configuración
```bash
# YAML: configuración de la prueba
cat > mi-prueba.yaml << 'EOF'
name: Mi Prueba Custom
simulationClass: bci.cards.simulation.BciLoginSmokeSimulation
mode: login
environment:
  BCI_LOGIN_URL: https://tu-endpoint.com/oauth/token
  BCI_LOGIN_BASIC_AUTH: base64_credentials
injection:
  atOnceUsers: 3
  duration: 30 seconds
EOF

# CSV: datos de usuarios
cat > usuarios.csv << 'EOF'
rutLogin,claveLogin,userName,environment,application
12345678-9,password123,user1,cert,movil
98765432-1,password456,user2,cert,movil
EOF
```

### Paso 2: Subir configuración
```bash
curl -X POST http://localhost:8080/gatling/gatling-gen3-app/v0.1/api/configurations \
  -F "configuration=@mi-prueba.yaml" \
  -F "users=@usuarios.csv" | jq .
```

**Respuesta:**
```json
{
  "configurationId": "cfg-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
}
```

### Paso 3: Ejecutar prueba
```bash
EXEC_ID=$(curl -s -X POST http://localhost:8080/gatling/gatling-gen3-app/v0.1/api/executions/cfg-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx | jq -r '.executionId')

# Monitorear progreso
curl -s http://localhost:8080/gatling/gatling-gen3-app/v0.1/api/executions/$EXEC_ID | jq .
```

---

## Formato YAML

### Estructura Completa
```yaml
# REQUERIDO: Nombre descriptivo de la prueba
name: Nombre de la Prueba

# REQUERIDO: Clase Scala que ejecutar (DEBE estar compilada en Docker)
# Opciones disponibles:
#   - bci.cards.simulation.BciLoginSmokeSimulation
#   - bci.cards.simulation.BcimsInformacionBasicaPeakSimulation
#   - bci.cards.simulation.BcimsInformacionBasicaTpsSimulation
#   - bci.cards.simulation.ConfigurableBcimsSimulation
#   - bci.cards.simulation.PublicMockSimulation
simulationClass: bci.cards.simulation.BciLoginSmokeSimulation

# REQUERIDO: Modo de operación
mode: login  # o: api, mock

# REQUERIDO: Variables de entorno (específicas por simulación)
environment:
  BCI_LOGIN_URL: https://bci-api-crt001.internal.bci.cl/operaciones/seguridad-y-acceso/ms-loginclientes-util/v1.3/oauth/token
  BCI_LOGIN_BASIC_AUTH: YXBwLW1vdmlsLXBlcnNvbmFzOnNlY3JldGlzaW1v
  TRACKING_ID_PREFIX: mi-prueba
  APP_ID: APP_MOVIL
  CHANNEL: movil
  REF_PREFIX: ref

# REQUERIDO: Configuración de inyección de usuarios
injection:
  atOnceUsers: 3           # Número de usuarios que inician simultáneamente
  duration: 30 seconds    # Duración total de la prueba

# OPCIONAL: SLA (Service Level Agreement)
sla:
  maxResponseTime: 3000   # Tiempo máximo de respuesta en ms
  failedRequests: 0       # Número máximo de fallos permitidos

# OPCIONAL: Assertions (validaciones)
assertions:
  - status 200            # Código HTTP esperado
  - responseTime < 3000   # Tiempo máximo de respuesta
```

### Variables de Entorno por Simulación

#### BciLoginSmokeSimulation
```yaml
environment:
  BCI_LOGIN_URL: https://url-del-endpoint/oauth/token
  BCI_LOGIN_BASIC_AUTH: credentials_en_base64
  TRACKING_ID_PREFIX: prefijo-único
  APP_ID: APP_MOVIL
  CHANNEL: movil
  REF_PREFIX: ref
```

#### BcimsInformacionBasicaPeakSimulation / BcimsInformacionBasicaTpsSimulation
```yaml
environment:
  BCI_LOGIN_URL: https://url-login/oauth/token
  BCI_LOGIN_BASIC_AUTH: credentials_en_base64
  BCI_API_URL: https://url-api/
  BCI_TARJETAS_ENDPOINT: /tarjetas
  BCI_SALDOS_ENDPOINT: /saldos
  BCI_DEUDA_ENDPOINT: /deuda
  APP_ID: APP_MOVIL
  CHANNEL: movil
```

#### PublicMockSimulation (para pruebas sin credenciales)
```yaml
environment:
  MOCK_URL: http://localhost:8080/mock
  SCENARIO_TYPE: login  # o: api, data
```

---

## Formato CSV

### Estructura Base: Login
```csv
rutLogin,claveLogin,userName,environment,application
10063842-8,111222,user1,cert,movil
10063843-6,111222,user2,cert,movil
10063844-4,111222,user3,cert,movil
```

**Campos:**
- `rutLogin`: RUT del usuario (formato: XX.XXX.XXX-X o XXXXXXXX-X)
- `claveLogin`: Contraseña del usuario
- `userName`: Nombre identificador del usuario
- `environment`: Ambiente (cert, prod, qa)
- `application`: Aplicación (movil, web, api)

### Estructura Extendida: Información Básica
```csv
rutLogin,claveLogin,cuenta,tarjeta,userName,environment,application
10063842-8,111222,100000000,4111111111111111,user1,cert,movil
10063843-6,111222,100000001,4111111111111112,user2,cert,movil
10063844-4,111222,100000002,4111111111111113,user3,cert,movil
```

**Campos adicionales:**
- `cuenta`: Número de cuenta del usuario
- `tarjeta`: Número de tarjeta (para pruebas de tarjetas)

### Restricciones
- ✅ Mínimo 1 usuario
- ✅ Máximo: sin límite (Gatling puede manejar miles)
- ✅ Caracteres especiales permitidos en contraseñas
- ❌ NO cambiar nombres de columnas
- ❌ NO agregar columnas que no use la simulación

---

## Crear Nueva Prueba

### Paso 1: Definir Requisitos
```
¿Qué quiero probar?
├─ Endpoint: https://api.ejemplo.com/operacion
├─ Usuarios: 5
├─ Duración: 2 minutos
├─ Timeout: 5000ms
└─ Credenciales: usuario/password
```

### Paso 2: Crear Archivo YAML
```yaml
name: Mi Operación Custom - 5 usuarios
simulationClass: bci.cards.simulation.BciLoginSmokeSimulation
mode: login

environment:
  # CAMBIAR: URL de tu endpoint
  BCI_LOGIN_URL: https://api.ejemplo.com/operacion
  # CAMBIAR: Credenciales en Base64
  BCI_LOGIN_BASIC_AUTH: dXNlcm5hbWU6cGFzc3dvcmQ=
  # PERSONALIZAR: Prefijos únicos para tracking
  TRACKING_ID_PREFIX: test-operacion-001
  APP_ID: MI_APP
  CHANNEL: api
  REF_PREFIX: ref-operacion

injection:
  atOnceUsers: 5          # ← CAMBIAR: cantidad de usuarios
  duration: 2 minutes     # ← CAMBIAR: duración (seconds, minutes, hours)

sla:
  maxResponseTime: 5000   # ← CAMBIAR: según SLA
  failedRequests: 0
```

### Paso 3: Crear Archivo CSV
```csv
rutLogin,claveLogin,userName,environment,application
11111111-1,pass123,usuario1,test,api
22222222-2,pass123,usuario2,test,api
33333333-3,pass123,usuario3,test,api
44444444-4,pass123,usuario4,test,api
55555555-5,pass123,usuario5,test,api
```

### Paso 4: Validar (Sin ejecutar)
```bash
# Verificar que los archivos existen
ls -lh mi-prueba.yaml usuarios.csv

# Verificar formato YAML (debe ser válido)
cat mi-prueba.yaml | grep -E "^[a-zA-Z].*:" | head -10

# Verificar CSV (header correcto)
head -1 usuarios.csv
```

### Paso 5: Subir y Ejecutar
```bash
# Subir
CONFIG_ID=$(curl -s -X POST http://localhost:8080/gatling/gatling-gen3-app/v0.1/api/configurations \
  -F "configuration=@mi-prueba.yaml" \
  -F "users=@usuarios.csv" | jq -r '.configurationId')

# Ejecutar
curl -X POST http://localhost:8080/gatling/gatling-gen3-app/v0.1/api/executions/$CONFIG_ID
```

---

## Ejemplos Completos

### Ejemplo 1: Prueba de Smoke Test (3 usuarios, 30 segundos)

**`smoke-test.yaml`**
```yaml
name: BCI Smoke Test
simulationClass: bci.cards.simulation.BciLoginSmokeSimulation
mode: login

environment:
  BCI_LOGIN_URL: https://bci-api-crt001.internal.bci.cl/operaciones/seguridad-y-acceso/ms-loginclientes-util/v1.3/oauth/token
  BCI_LOGIN_BASIC_AUTH: YXBwLW1vdmlsLXBlcnNvbmFzOnNlY3JldGlzaW1v
  TRACKING_ID_PREFIX: smoke-test
  APP_ID: APP_MOVIL
  CHANNEL: movil
  REF_PREFIX: ref

injection:
  atOnceUsers: 3
  duration: 30 seconds

sla:
  maxResponseTime: 3000
  failedRequests: 0

assertions:
  - status 200
  - responseTime < 3000
```

**`smoke-users.csv`**
```csv
rutLogin,claveLogin,userName,environment,application
10063842-8,111222,user1,cert,movil
10063843-6,111222,user2,cert,movil
10063844-4,111222,user3,cert,movil
```

---

### Ejemplo 2: Prueba de Carga - Peak (10 usuarios, 5 minutos)

**`peak-test.yaml`**
```yaml
name: Peak Load Test - 10 Users
simulationClass: bci.cards.simulation.BcimsInformacionBasicaPeakSimulation
mode: api

environment:
  BCI_LOGIN_URL: https://bci-api-crt004.internal.bci.cl/oauth/token
  BCI_LOGIN_BASIC_AUTH: YXBwLW1vdmlsLXBlcnNvbmFzOnNlY3JldGlzaW1v
  BCI_API_URL: https://bci-api-crt004.internal.bci.cl/
  BCI_TARJETAS_ENDPOINT: /operaciones/tarjeta/v1/obtenerListaTarjetas
  BCI_SALDOS_ENDPOINT: /operaciones/cuentas/v1/obtenerListaSaldos
  BCI_DEUDA_ENDPOINT: /operaciones/tarjeta/v1/obtenerDeuda
  APP_ID: APP_MOVIL
  CHANNEL: movil

injection:
  atOnceUsers: 10
  duration: 5 minutes

sla:
  maxResponseTime: 5000
  failedRequests: 2

assertions:
  - status 200
  - responseTime < 5000
```

**`peak-users.csv`**
```csv
rutLogin,claveLogin,cuenta,tarjeta,userName,environment,application
10063842-8,111222,100000000,4111111111111111,user1,cert,movil
10063843-6,111222,100000001,4111111111111112,user2,cert,movil
10063844-4,111222,100000002,4111111111111113,user3,cert,movil
10063845-2,111222,100000003,4111111111111114,user4,cert,movil
10063846-0,111222,100000004,4111111111111115,user5,cert,movil
10063847-8,111222,100000005,4111111111111116,user6,cert,movil
10063848-6,111222,100000006,4111111111111117,user7,cert,movil
10063849-4,111222,100000007,4111111111111118,user8,cert,movil
10063850-2,111222,100000008,4111111111111119,user9,cert,movil
10063851-0,111222,100000009,4111111111111120,user10,cert,movil
```

---

### Ejemplo 3: Prueba Mock (sin credenciales reales)

**`mock-test.yaml`**
```yaml
name: Mock HttpBin Test
simulationClass: bci.cards.simulation.PublicMockSimulation
mode: mock

environment:
  MOCK_URL: http://httpbin.org
  SCENARIO_TYPE: login

injection:
  atOnceUsers: 5
  duration: 1 minute

sla:
  maxResponseTime: 2000
  failedRequests: 0
```

**`mock-users.csv`**
```csv
rutLogin,claveLogin,userName,environment,application
mock-user-1,mock-pass,mock1,test,mock
mock-user-2,mock-pass,mock2,test,mock
mock-user-3,mock-pass,mock3,test,mock
mock-user-4,mock-pass,mock4,test,mock
mock-user-5,mock-pass,mock5,test,mock
```

---

## Endpoints API

### 1. Subir Configuración
```bash
POST /api/configurations
Content-Type: multipart/form-data

Form Data:
  - configuration: archivo.yaml
  - users: usuarios.csv

Response:
{
  "configurationId": "cfg-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
}
```

### 2. Obtener Configuración
```bash
GET /api/configurations/{configurationId}

Response:
{
  "configurationId": "cfg-xxx",
  "name": "Mi Prueba",
  "simulationClass": "bci.cards.simulation...",
  "uploadedAt": "2026-07-17T14:00:00Z"
}
```

### 3. Ejecutar Prueba
```bash
POST /api/executions/{configurationId}

Response:
{
  "executionId": "run-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "status": "QUEUED"
}
```

### 4. Obtener Estado de Ejecución
```bash
GET /api/executions/{executionId}

Response:
{
  "executionId": "run-xxx",
  "configurationId": "cfg-xxx",
  "status": "SUCCESS|RUNNING|QUEUED|FAILED",
  "exitCode": 0,
  "failureType": null,
  "message": "Prueba finalizada",
  "startedAt": "2026-07-17T14:00:00Z",
  "finishedAt": "2026-07-17T14:00:16Z"
}
```

### Estados Posibles
- `QUEUED`: Esperando a ser ejecutada
- `RUNNING`: En ejecución
- `SUCCESS`: Finalizada exitosamente
- `TEST_FAILED`: Prueba ejecutó pero con fallos (assertions)
- `PLATFORM_ERROR`: Error en el sistema/contenedor

### 5. Stream de Logs (SSE)
```bash
GET /api/executions/{executionId}/stream

Response: Server-Sent Events con logs en tiempo real
```

---

## Archivos Modificables

### ✅ PUEDES MODIFICAR

#### 1. **YAML de Configuración** (el que subes a la API)
```yaml
✅ name:                    # Cambia el nombre de la prueba
✅ environment:            # Cambia URLs, credenciales, prefijos
✅ injection.atOnceUsers:  # Número de usuarios
✅ injection.duration:     # Duración de la prueba
✅ sla.maxResponseTime:    # Timeout
✅ sla.failedRequests:     # Fallos permitidos
✅ assertions:             # Validaciones esperadas
```

#### 2. **CSV de Usuarios** (el que subes a la API)
```csv
✅ Cualquier dato en las columnas existentes
✅ Agregar más filas de usuarios
✅ Cambiar valores de RUT, contraseña, ambiente
```

#### 3. **Código Scala de Simulaciones** (en `src/gatling/scala/`)
```scala
✅ Modificar lógica de test en simulaciones existentes
✅ Cambiar endpoints
✅ Agregar nuevas assertions
✅ Modificar patrones de inyección
```

**Después de modificar código Scala → DEBES reconstruir Docker:**
```bash
docker build -t gatling-control-api:latest .
docker stop gatling-test-smoke
docker run -d --name gatling-test-smoke -p 8080:8080 gatling-control-api:latest
```

---

### ❌ NO CAMBIES

#### 1. **simulationClass** (a menos que sepas que existe compilada)
```yaml
# ❌ Esto NO funcionará si la clase no está compilada en Docker
simulationClass: bci.cards.simulation.MiSimulacionCustom

# ✅ Usa solo las que existen en /opt/gatling/classes/
# Verificar disponibles:
docker exec gatling-test-smoke find /opt/gatling/classes -name "*.class" | grep -i simulation
```

#### 2. **Nombres de Columnas CSV**
```csv
# ❌ NO hagas esto (la simulación no reconocerá las columnas)
rutLogin,claveLogin,usuario_name,env,app

# ✅ Usa exactamente los nombres esperados
rutLogin,claveLogin,userName,environment,application
```

#### 3. **Estructura de Directorios Docker**
```
❌ /app/data/           → Datos persistentes (genera automáticamente)
❌ /opt/gatling/        → Runtime de Gatling (generado en build)
❌ /opt/gatling/classes/  → Clases compiladas (generado en build)
```

#### 4. **Base Path de la API**
```
❌ NO cambies: /gatling/gatling-gen3-app/v0.1
✅ Este es fijo en Spring Boot
```

---

## Solución de Problemas

### Problema: "simulationClass no está en la whitelist"

**Síntomas:**
- Ejecución queda en QUEUED indefinidamente
- Logs: `Class not in whitelist`

**Solución:**
1. Verifica que la clase existe compilada:
```bash
docker exec gatling-test-smoke find /opt/gatling/classes -name "*.class"
```

2. Si no existe, rebuild Docker:
```bash
docker build -t gatling-control-api:latest .
```

3. Si existe, verifica que está en `ExecutionService.java`:
```bash
grep -A 10 "resolveSimulationClass" src/main/java/cl/bci/performance/api/service/ExecutionService.java
```

---

### Problema: "users.csv requerido pero no encontrado"

**Síntomas:**
- `FileNotFoundException` en logs
- Ejecución falla inmediatamente

**Solución:**
1. Verifica que subiste el CSV:
```bash
curl -s http://localhost:8080/gatling/gatling-gen3-app/v0.1/api/configurations/cfg-xxx | jq .
```

2. Para simulaciones que NO necesitan CSV:
- `PublicMockSimulation` → **NO requiere CSV**
- Todas las BCI → **SÍ requieren CSV**

---

### Problema: "YAML parsing error"

**Síntomas:**
- `Error parsing YAML`
- Configuración no se sube

**Solución:**
1. Valida YAML en línea: https://www.yamllint.com/
2. Asegúrate de indentación de 2 espacios (no tabs)
3. Verifica que `environment:` tiene sub-claves indentadas

**Ejemplo correcto:**
```yaml
environment:
  BCI_LOGIN_URL: https://...
  BCI_LOGIN_BASIC_AUTH: ...
```

**Ejemplo incorrecto:**
```yaml
environment:
BCI_LOGIN_URL: https://...  # ← Falta indentación
```

---

### Problema: "CSV header inválido"

**Síntomas:**
- Simulación ejecuta pero fallos en todas las peticiones
- Error `column not found`

**Solución:**
1. Verifica primera línea del CSV:
```bash
head -1 usuarios.csv
```

2. Debe coincidir exactamente con lo que la simulación espera:
```bash
# Para BciLoginSmokeSimulation
rutLogin,claveLogin,userName,environment,application

# Para BcimsInformacionBasica*
rutLogin,claveLogin,cuenta,tarjeta,userName,environment,application
```

---

### Problema: "Timeout conectando a endpoint"

**Síntomas:**
- `ConnectException`
- Todas las peticiones fallan
- `max response time exceeded`

**Solución:**
1. Verifica que la URL es correcta:
```bash
curl -v https://tu-endpoint/oauth/token 2>&1 | head -10
```

2. Aumenta timeout en SLA:
```yaml
sla:
  maxResponseTime: 10000  # De 3000 a 10000ms
```

3. Verifica que no hay firewall/VPN bloqueando

---

### Problema: "HTTP 401/403 en peticiones"

**Síntomas:**
- `HTTP 401 Unauthorized`
- `HTTP 403 Forbidden`

**Solución:**
1. Verifica credenciales Base64:
```bash
# Codificar
echo -n "usuario:password" | base64

# Decodificar (verificar)
echo "dXNlcm5hbWU6cGFzc3dvcmQ=" | base64 -d
```

2. Usa el valor correcto en YAML:
```yaml
environment:
  BCI_LOGIN_BASIC_AUTH: dXNlcm5hbWU6cGFzc3dvcmQ=
```

---

## Referencia Rápida

### Duración de Inyección
```
10 seconds      → 10s
1 minute        → 60s
2 minutes       → 120s
1 hour          → 3600s
```

### Conversión a Base64
```bash
# Codificar
echo -n "string" | base64

# Decodificar
echo "string_base64" | base64 -d
```

### Verificar Container Healthy
```bash
docker ps | grep gatling-test
# Status debe ser: Up X seconds (healthy)

docker exec gatling-test-smoke \
  curl -s http://localhost:8080/gatling/gatling-gen3-app/v0.1/actuator/health | jq .status
# Debe retornar: "UP"
```

### Ver Simulaciones Disponibles
```bash
docker exec gatling-test-smoke \
  find /opt/gatling/classes -path "*simulation*.class" -type f | \
  sed 's|.*classes/||' | sed 's|\.class||' | sort
```

---

## Próximos Pasos

1. **Personalizar YAML y CSV** con tus datos
2. **Probar localmente** con 3-5 usuarios primero
3. **Monitorear logs**: `docker logs -f gatling-test-smoke`
4. **Descargar reportes**: Ubicados en `/app/data/executions/{executionId}/results/`
5. **Escalar**: Aumentar usuarios gradualmente y validar resultados

---

**¿Preguntas?** Consulta los ejemplos o revisa los logs de ejecución.

# Upload Simulación Scala - Guía Completa

## Descripción General

Nuevo endpoint API que permite:
1. ✅ **Subir** archivo Scala (.scala)
2. ✅ **Validar** sintaxis y estructura
3. ✅ **Compilar** en runtime
4. ✅ **Sugerir correcciones** si hay errores
5. ✅ **Ejecutar** simulación inmediatamente

## Flujo Completo

```
1. Usuario sube archivo .scala
          ↓
2. Sistema valida:
   - Package correcto
   - Clase correcta  
   - Extiende Simulation
   - Imports Gatling
          ↓
3. Si ERROR de validación:
   ❌ Retorna errores + sugerencias
   ❌ NO continúa
          ↓
4. Si OK, compila con scalac:
   - Classpath: /opt/gatling/lib/*
   - Output: /opt/gatling/classes/
   - Timeout: 30 segundos
          ↓
5. Si ERROR de compilación:
   ❌ Retorna línea exacta + sugerencia
   ❌ NO continúa
          ↓
6. Si OK:
   ✅ Agrega a whitelist runtime
   ✅ Crea configuración temporal
   ✅ Ejecuta simulación
   ✅ Retorna execution ID + stream URL
```

## Endpoint API

### POST /api/upload-and-execute

**Multipart Form Data:**

| Campo | Tipo | Obligatorio | Descripción |
|-------|------|-------------|-------------|
| `file` | File | ✅ | Archivo .scala |
| `simulationClass` | String | ✅ | Clase esperada (ej: `bci.cards.simulation.MySimulation`) |
| `mode` | String | ❌ | Modo: `login`, `api`, `mock` (default: `api`) |
| `configYaml` | File | ❌ | YAML custom (sino usa defaults) |

**Ejemplo cURL:**

```bash
curl -X POST \
  -F "file=@BciCustomSimulation.scala" \
  -F "simulationClass=bci.cards.simulation.BciCustomSimulation" \
  -F "mode=login" \
  http://localhost:8080/gatling/gatling-gen3-app/v0.1/api/upload-and-execute
```

## Response Status

### ✅ Éxito (202 Accepted)

```json
{
  "status": "SUCCESS",
  "message": "✅ Código compilado y ejecución iniciada",
  "file": "BciCustomSimulation.scala",
  "simulationClass": "bci.cards.simulation.BciCustomSimulation",
  "configurationId": "cfg-abc123",
  "executionId": "run-xyz789",
  "streamUrl": "/api/executions/run-xyz789/logs/stream"
}
```

**Acciones siguientes:**
```bash
# Ver logs en tiempo real
curl -N "http://localhost:8080/gatling/gatling-gen3-app/v0.1/api/executions/run-xyz789/logs/stream"

# Verificar estado
curl "http://localhost:8080/gatling/gatling-gen3-app/v0.1/api/executions/run-xyz789"

# Descargar reporte
curl -O "http://localhost:8080/gatling/gatling-gen3-app/v0.1/api/executions/run-xyz789/report"
```

---

### ❌ Error de Validación (400 Bad Request)

```json
{
  "status": "VALIDATION_ERROR",
  "file": "BciCustomSimulation.scala",
  "simulationClass": "bci.cards.simulation.BciCustomSimulation",
  "errors": [
    {
      "message": "❌ Package incorrecto: 'bci.cards.test' vs esperado 'bci.cards.simulation'",
      "lineNumber": -1,
      "lineContent": "package bci.cards.test;",
      "source": "Validation"
    },
    {
      "message": "❌ La clase no extiende Simulation",
      "lineNumber": -1,
      "lineContent": "",
      "source": "Validation"
    }
  ],
  "suggestions": [
    "💡 Cambiar a: package bci.cards.simulation",
    "💡 Cambiar: class BciCustomSimulation extends Simulation"
  ]
}
```

---

### ❌ Error de Compilación (400 Bad Request)

```json
{
  "status": "COMPILATION_ERROR",
  "file": "BciCustomSimulation.scala",
  "simulationClass": "bci.cards.simulation.BciCustomSimulation",
  "errors": [
    {
      "message": "not found: value httpProtocol",
      "lineNumber": 25,
      "lineContent": "  val scn = scenario(\"Test\").exec(httpProtocol)",
      "source": "Scala Compiler"
    }
  ],
  "suggestions": [
    "💡 Verificar que el import está correcto",
    "💡 Asegurarse que la clase/objeto existe",
    "💡 Revisar la línea 25 del archivo"
  ]
}
```

---

## Estructura de Archivo Scala Válido

### Template Mínimo

```scala
package bci.cards.simulation

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class BciCustomSimulation extends Simulation {
  
  // Definir protocolo HTTP (si aplica)
  val httpConf = http
    .baseUrl("https://api.example.com")
    .acceptHeader("application/json")

  // Definir escenario
  val scn = scenario("CustomScenario")
    .exec(http("request_1")
      .get("/endpoint"))

  // Setup
  setUp(
    scn.inject(atOnceUsers(1))
  ).protocols(httpConf)
}
```

### Template Login (con autenticación)

```scala
package bci.cards.simulation

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class BciCustomLoginSimulation extends Simulation {
  
  val httpConf = http
    .baseUrl(sys.env.getOrElse("BCI_LOGIN_URL", "https://bci-api-cert.internal.bci.cl"))
    .acceptHeader("application/json")

  // Feeder (datos de usuarios)
  val users = csv("users.csv").random

  // Escenario con autenticación
  val scn = scenario("LoginFlow")
    .feed(users)
    .exec(http("login")
      .post("/oauth/token")
      .basicAuth(
        sys.env.getOrElse("BCI_LOGIN_USER", "app-user"),
        sys.env.getOrElse("BCI_LOGIN_PASS", "password")
      )
      .formParam("username", "${rutLogin}")
      .formParam("password", "${claveLogin}")
      .check(status.is(200))
      .check(jsonPath("$.access_token").exists))

  // Setup
  setUp(
    scn.inject(rampUsers(10).over(1 minute))
  ).protocols(httpConf)
}
```

---

## Validaciones Automáticas

| Validación | Error si falta |
|------------|-------------------|
| **Package correcto** | ❌ "Package incorrecto" |
| **Clase con nombre esperado** | ❌ "No se encontró clase" |
| **Extiende Simulation** | ❌ "La clase no extiende Simulation" |
| **Imports Gatling** | ⚠️ Aviso (pero continúa) |
| **Compilación escalac** | ❌ "Compilation Error + línea exacta" |

---

## Manejo de Errores Comunes

### Error: "Package incorrecto"

**Causa:** El package no coincide con la clase solicitada

**Solución:**
```scala
// ❌ INCORRECTO
package bci.cards.test

// ✅ CORRECTO
package bci.cards.simulation
```

---

### Error: "not found: value x"

**Causa:** Variable no definida o import faltante

**Solución:**
```scala
// ❌ INCORRECTO
val scn = scenario("Test").exec(http(...))  // http no definido

// ✅ CORRECTO
import io.gatling.http.Predef._  // Agregar import
val scn = scenario("Test").exec(http(...))
```

---

### Error: "Compilation excedió timeout"

**Causa:** La compilación tardó más de 30 segundos

**Solución:**
- Revisar si hay error sintáctico grave
- Reducir complejidad del código
- Asegurar que los imports existen

---

## Limitaciones & Notas Importantes

⚠️ **Considerar:**

1. **Persistencia:** La clase compilada se mantiene SOLO mientras el servidor esté activo
   - Si reinicia Docker → Se pierde la compilación
   - Usar Opción 2 (git commit) para persistencia

2. **Seguridad:** Valida imports pero NO hace sandboxing completo
   - No usar en producción desatendida
   - Revisar código sospechoso antes de subir

3. **Concurrencia:** Si múltiples usuarios suben simultáneamente
   - Se hace compilación secuencial (sin paralelismo)
   - Primera completada se ejecuta

4. **Recursos:** Cada compilación consume ~300MB RAM
   - Si servidor tiene límite bajo, puede fallar
   - Recomendado: mínimo 512MB disponible

---

## Diferencias con Opción 2 (Git Commit)

| Aspecto | Upload Runtime | Git Commit |
|---------|-----------------|-----------|
| **Upload** | ✅ | ✅ |
| **Compilación** | En servidor | En docker build |
| **Persistencia** | ❌ Temporal | ✅ Permanente |
| **Tiempo** | ~10 seg | ~5 min (rebuild) |
| **Para Dev** | ✅ Ideal | Lento |
| **Para Prod** | ❌ No recomendado | ✅ Recomendado |

---

## Próximos Pasos

Después de ejecutar:

1. **Ver logs en vivo:**
   ```bash
   curl -N "http://localhost:8080/gatling/gatling-gen3-app/v0.1/api/executions/{executionId}/logs/stream"
   ```

2. **Esperar completación:**
   ```bash
   # Revisar estado cada 10 seg
   curl "http://localhost:8080/gatling/gatling-gen3-app/v0.1/api/executions/{executionId}"
   ```

3. **Descargar reporte:**
   ```bash
   curl -O "http://localhost:8080/gatling/gatling-gen3-app/v0.1/api/executions/{executionId}/report"
   unzip *.zip
   open index.html  # o desde archivo local
   ```

4. **Si resultó OK:** Guardar en git
   ```bash
   git add src/gatling/scala/bci/cards/simulation/BciCustomSimulation.scala
   git commit -m "add: BciCustomSimulation"
   git push origin main
   ```

5. **En cloud:** Rebuild Docker
   ```bash
   docker build -t gatling-control-api:latest .
   ```

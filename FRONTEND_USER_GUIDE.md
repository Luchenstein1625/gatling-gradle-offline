# 🎨 Frontend UI - Cómo Usar el Upload Scala

## 📸 Vista Actual de la Aplicación

```
╔════════════════════════════════════════════════════════════════════════════════╗
║                         Gatling Control API                                    ║
║                 Java + Spring Boot + Scala + Gatling                           ║
╚════════════════════════════════════════════════════════════════════════════════╝

┌─────────────────────────┬─────────────────────────┬─────────────────────────┐
│                         │                         │                         │
│  1. Plantilla           │  1b. ✨ Upload Scala    │  2. Subir Configuración │
│  ─────────────────────  │  ─────────────────────  │  ─────────────────────  │
│                         │                         │                         │
│ [▼] Seleccionar         │ [+] Archivo .scala      │ [+] YAML                │
│     Plantilla           │     (requerido)         │ [+] CSV usuarios        │
│                         │                         │                         │
│ [Descargar]  [Swagger]  │ [+] Config YAML         │ [Plantilla CSV]         │
│                         │     (opcional)          │                         │
│                         │                         │ [Subir]                 │
│                         │ [▼] Modo de             │                         │
│                         │     Simulación          │ Config ID: -            │
│                         │   ◉ LOAD                │                         │
│                         │   ○ PEAK                │                         │
│                         │   ○ SMOKE               │                         │
│                         │                         │                         │
│                         │ [💚 Compilar y Ejecutar]│                         │
│                         │                         │                         │
│                         │ Estado: -               │                         │
│                         │                         │                         │
└─────────────────────────┴─────────────────────────┴─────────────────────────┘

┌─────────────────────────┬─────────────────────────┐
│                         │                         │
│  3. Ejecutar            │  4. Recursos            │
│  ─────────────────────  │  ─────────────────────  │
│                         │                         │
│ [Ejecutar Prueba]       │ [Actualizar]            │
│                         │                         │
│ Execution ID: -         │ {                       │
│ Estado: -               │   "memory": {...},      │
│ [Descargar Reporte]     │   "timestamp": "..."    │
│                         │ }                       │
│                         │                         │
└─────────────────────────┴─────────────────────────┘

┌────────────────────────────────────────────────────────────────────────────────┐
│  5. Log en vivo                                                                │
│  ─────────────────────────────────────────────────────────────────────────────  │
│                                                                                │
│  [esperando conexión...]                                                      │
│                                                                                │
│                                                                                │
└────────────────────────────────────────────────────────────────────────────────┘
```

---

## ✨ Paso 1: Seleccionar Archivo Scala

### Acción en la UI

En la sección **"1b. ✨ Upload Scala Dinámico"**, haz clic en el campo:

```
[+] Archivo .scala (simulación personalizada)
    └─ Click aquí para abrir selector de archivos
```

### Archivo Esperado

Se aceptan **solo archivos `.scala`**:

```
✅ Aceptados:
  - DemoSimulation.scala
  - CustomSimulation.scala
  - MyLoadTest.scala
  
❌ Rechazados:
  - DemoSimulation.java (no es .scala)
  - simulation.yml (no es .scala)
  - simulation.scala.txt (extensión incorrecta)
```

### Ejemplo de Contenido Válido

```scala
class DemoSimulation extends Simulation {
  val httpProtocol = http
    .baseUrl("https://jsonplaceholder.typicode.com")
    .acceptHeader("application/json")

  val scn = scenario("Demo Posts API")
    .exec(http("GET /posts")
      .get("/posts")
      .check(status.is(200)))
    .pause(1, 2)

  setUp(
    scn.inject(atOnceUsers(5))
  ).protocols(httpProtocol)
    .assertions(
      global.successfulRequests.percent.gt(95)
    )
}
```

---

## 🔧 Paso 2: Configuración Opcional (YAML)

### ¿Cuándo es Necesario?

El YAML es **opcional**. Úsalo si quieres:
- Cambiar inyecciones (ramp-up, duración)
- Agregar SLAs (Service Level Agreements)
- Configurar headers/autenticación globales
- Definir assertions adicionales

### Formato YAML

```yaml
name: Demo Load Test
simulationClass: bci.cards.simulation.DemoSimulation
mode: PEAK

injection:
  atOnceUsers: 10
  duration: 5 minutes

sla:
  maxResponseTime: 3000
  failedRequests: 0

assertions:
  - global.successfulRequests.percent > 95
  - global.responseTime.percentile95 < 2000
```

### Sin YAML

Si no seleccionas YAML, se usará configuración **por defecto**:

```yaml
name: Custom Simulation Runtime Upload
simulationClass: bci.cards.simulation.{TuClase}
mode: LOAD
injection:
  atOnceUsers: 1
  duration: 30 seconds
sla:
  maxResponseTime: 3000
  failedRequests: 0
```

---

## 📊 Paso 3: Seleccionar Modo

### Modos Disponibles

```
┌──────────────────────────────────────────────────┐
│ Modo de simulación                               │
├──────────────────────────────────────────────────┤
│ ◉ LOAD (TPS constante)                          │
│   - Usuarios: constante                          │
│   - Uso: Baseline performance testing            │
│   - Duración típica: 5-30 minutos                │
│                                                  │
│ ○ PEAK (carga máxima)                           │
│   - Usuarios: ramp-up 100% → mantener           │
│   - Uso: Stress testing, encontrar límites      │
│   - Duración típica: 10-15 minutos              │
│                                                  │
│ ○ SMOKE (prueba rápida)                         │
│   - Usuarios: muy pocos (1-5)                    │
│   - Uso: Verificación rápida de API             │
│   - Duración: 1-2 minutos                        │
└──────────────────────────────────────────────────┘
```

### Recomendaciones

```
🟢 LOAD (DEFAULT)
   Use para: Testing regular de API
   Duración: 5-10 minutos
   Usuarios: 10-50 simultáneos
   
🟡 PEAK
   Use para: Stress test, encontrar límites
   Duración: 10-15 minutos
   Usuarios: 100-500 simultáneos
   ⚠️ ESPERAR: Puede tomar 15-20 minutos
   
🔵 SMOKE
   Use para: Verificación rápida antes de PEAK
   Duración: 1-2 minutos
   Usuarios: 1-5 simultáneos
```

---

## 🚀 Paso 4: Compilar y Ejecutar

### Botón "Compilar y Ejecutar"

```
┌─────────────────────────────────┐
│  💚 Compilar y Ejecutar        │  ← Click aquí
└─────────────────────────────────┘
```

### Lo Que Sucede Internamente

```
1. UPLOAD
   POST /api/upload-and-execute
   └─ Envía: scalaFile, configYaml (opt), simulationMode
   
2. VALIDACIÓN
   └─ ✅ Archivo es .scala
   └─ ✅ No contiene code injection (System.exit, reflection, etc.)
   
3. COMPILACIÓN
   └─ gradle compileGatlingScala
   └─ src/gatling/scala/DemoSimulation.scala → bytecode
   └─ Tiempo: 8-15 segundos
   
4. VALIDACIÓN CLASSPATH
   └─ Class.forName("bci.cards.simulation.DemoSimulation")
   └─ Verifica que la clase compilada existe
   
5. EJECUCIÓN
   └─ ExecutionService.start(configurationId)
   └─ ProcessBuilder → gatling-runner.sh
   └─ Inicia simulación
   
6. RESPONSE
   {
     "executionId": "run-a1b2c3d4-...",
     "simulationClass": "bci.cards.simulation.DemoSimulation",
     "status": "RUNNING"
   }
```

---

## 📝 Paso 5: Monitor en Vivo

### Estados Mostrados

```
┌─────────────────────────────────┐
│  Estado                         │
├─────────────────────────────────┤
│ ⏳ Compilando y ejecutando...    │ (1-3 segundos)
│                                 │
│ ✅ Compilado. Ejecutando:        │ (durante ejecución)
│    bci.cards.simulation.Demo... │
│                                 │
│ RUNNING                         │ (en sección "3. Ejecutar")
│                                 │
│ SUCCESS                         │ (después de completar)
│                                 │
│ TEST_FAILED                     │ (si hubo errores)
│                                 │
│ ❌ Contenido peligroso           │ (validación rechazó)
│    detectado: System.exit       │
└─────────────────────────────────┘
```

### Logs en Vivo

En sección **"5. Log en vivo"** aparecerán (SSE stream):

```
⏳ Compiling Scala file: DemoSimulation.scala
Validating content...
✅ Compilation successful
Starting Gatling simulation: bci.cards.simulation.DemoSimulation
[Gatling] Simulations [...]
[Gatling] Starting simulation...
[Gatling] Injection: 5 at once
[Gatling] Scenario 'Demo Posts API' starting...
[Gatling] ========================================
[Gatling] ---- Demo Posts API ----
[Gatling] Count                 5
[Gatling] Mean                  234.50
[Gatling] Stddev                45.67
[Gatling] Min                   123
[Gatling] Max                   456
[Gatling] ========================================
[Gatling] Simulation complete
Report generated at: http://...
```

---

## 📊 Paso 6: Descargar Reporte

### Después de Completar

Cuando el estado cambia a **SUCCESS**, aparece disponible:

```
┌─────────────────────────────────┐
│  [Descargar Reporte]            │ ← Click para descargar
└─────────────────────────────────┘
```

### Contenido del Reporte

```
Gatling HTML Report
├─ index.html (archivo principal)
├─ js/ (gráficos interactivos)
├─ css/ (estilos)
└─ assets/

Visualizaciones:
  ✓ Response Time Distribution
  ✓ Success Rate Over Time
  ✓ Requests Per Second
  ✓ Response Time Percentiles
  ✓ Detailed Scenario Timeline
```

---

## 🔴 Manejo de Errores

### Error 1: Archivo No Seleccionado

```
Acción:     Click "Compilar y Ejecutar" sin seleccionar archivo
Resultado:  Alert: "Selecciona un archivo .scala"
Solución:   Selecciona un archivo .scala válido
```

### Error 2: Archivo No es .scala

```
Acción:     Upload MySimulation.java
Resultado:  Alert: "Solo archivos .scala son permitidos"
Solución:   Renombra a .scala o usa archivo correcto
```

### Error 3: Code Injection Detectado

```
Acción:     Upload archivo con System.exit(0)
Resultado:  Estado: "❌ Contenido peligroso detectado: System.exit"
Solución:   Remueve código peligroso de la simulación
```

### Error 4: Compilación Fallida

```
Acción:     Upload archivo con sintaxis Scala inválida
Resultado:  Estado: "❌ Compilación Scala fallida (exit code: 1)"
           + Logs muestran: "error: expected class or object definition"
Solución:   Corrige la sintaxis Scala
```

### Error 5: Clase No Compilada

```
Acción:     Upload MySimulation.scala pero no define clase
Resultado:  Alert: "Clase no encontrada: bci.cards.simulation.MySimulation"
Solución:   Asegúrate que el archivo define: class NombreArchivo extends Simulation
```

---

## 🎯 Flujo Completo Ejemplo

### Archivo: DemoSimulation.scala

```scala
class DemoSimulation extends Simulation {
  val http = http
    .baseUrl("https://jsonplaceholder.typicode.com")
    .acceptHeader("application/json")
  
  val scn = scenario("Read Posts")
    .exec(http("GET /posts")
      .get("/posts")
      .check(status.is(200)))
  
  setUp(scn.inject(atOnceUsers(5))).protocols(httpProtocol)
}
```

### Pasos en la UI

```
1. Open: http://localhost:8080/
   
2. Scroll to: "1b. ✨ Upload Scala Dinámico"

3. Click: [+] Archivo .scala
   └─ Browse → Select "DemoSimulation.scala"
   
4. Leave: [+] Config YAML (sin seleccionar)

5. Select: [▼] Modo de simulación
   └─ Choose: LOAD
   
6. Click: [💚 Compilar y Ejecutar]
   
7. Watch: Estado → "⏳ Compilando y ejecutando..."
           (espera ~8-10 segundos)
   
8. See: Estado → "✅ Compilado. Ejecutando: bci.cards.simulation.DemoSimulation"
   
9. Monitor: "5. Log en vivo" muestra progreso
   
10. Wait: Estado en "3. Ejecutar" cambia a "SUCCESS"
   
11. Download: Haz click "Descargar Reporte"
    └─ Abre HTML con gráficos en navegador
```

---

## 💡 Tips & Tricks

### Tip 1: Usar SMOKE primero

```
1. Upload con SMOKE (1-2 minutos)
2. Verifica que API responde
3. Si OK → Ejecuta con PEAK
```

### Tip 2: Múltiples Uploads

```
Puedes subir varias simulaciones diferentes:
- DemoSimulation.scala (API pública)
- LoginSimulation.scala (auth test)
- DataSimulation.scala (bulk operations)

El sistema mantiene history de ejecuciones.
```

### Tip 3: Reutilizar YAML

Guarda un YAML que funcione:
```yaml
name: Mi Template
mode: PEAK
injection:
  atOnceUsers: 20
  duration: 10 minutes
```

Y cargalo varias veces con diferentes Scala files.

### Tip 4: Leer Logs Durante Ejecución

Mientras ejecuta, puedes:
- Ver logs en vivo en "5. Log en vivo"
- Ver estado en "3. Ejecutar"
- Leer reportes parciales en navegador

---

## 📞 Soporte

### Si falla la compilación:
1. Verifica sintaxis Scala (copy-paste de ejemplos Gatling)
2. No uses patrones peligrosos (System.exit, reflection)
3. Verifica que extends Simulation

### Si no aparece el reporte:
1. Espera a que estado sea SUCCESS
2. Verifica logs en "5. Log en vivo"
3. Check: /api/executions/{executionId}/report en navegador

### Si API no responde:
1. Verifica salud: http://localhost:8080/gatling/gatling-gen3-app/v0.1/actuator/health
2. Check container logs: `docker logs <container>`
3. Restart: `docker restart <container>`

---

✅ **Feature LISTO para usar**  
🎨 **UI COMPLETA**  
📚 **Documentado**  

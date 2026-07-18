# 🎨 Frontend - Scala Upload UI Guide

**Status**: ✅ Implementado  
**Date**: 2026-07-17  
**Location**: `src/main/resources/static/index.html`

---

## 📸 Vista de la UI

La interfaz ahora tiene **5 secciones principales**:

```
┌─────────────────────────────────────────┐
│ 1. Plantilla                            │
│    (Descargar YAML template)            │
│                                         │
│ 1b. ✨ Upload Scala Dinámico            │ ← NUEVO
│    (Compilar + ejecutar en runtime)     │
│                                         │
│ 2. Subir configuración                  │
│    (Config YAML + CSV usuarios)         │
│                                         │
│ 3. Ejecutar                             │
│    (Ejecutar prueba pre-compilada)      │
│                                         │
│ 4. Recursos                             │
│    (JVM memory stats)                   │
│                                         │
│ 5. Log en vivo                          │
│    (Stream SSE de logs)                 │
└─────────────────────────────────────────┘
```

---

## 🆕 Sección 1b: Upload Scala Dinámico

### Campos de Input

```html
<label>Archivo .scala (simulación personalizada)</label>
<input id="scalaFile" type="file" accept=".scala">

<label>Configuración YAML (opcional)</label>
<input id="scalaConfig" type="file" accept=".yaml,.yml">

<label>Modo de simulación</label>
<select id="simulationMode">
  <option value="LOAD">LOAD (TPS constante)</option>
  <option value="PEAK">PEAK (carga máxima)</option>
  <option value="SMOKE">SMOKE (prueba rápida)</option>
</select>

<button onclick="uploadAndExecuteScala()" 
        style="background:#10b981">
  Compilar y Ejecutar
</button>
```

### Elementos Descripción

| Elemento | Tipo | Requerido | Descripción |
|----------|------|-----------|-----------|
| scalaFile | File Input | ✅ Sí | Archivo `.scala` con la simulación |
| scalaConfig | File Input | ❌ No | YAML con configuración (inyecciones, SLAs) |
| simulationMode | Select | ✅ Sí | Modo de simulación (default: LOAD) |
| Botón | Button | - | Dispara compilación + ejecución automática |

---

## 🔧 JavaScript Function: `uploadAndExecuteScala()`

```javascript
async function uploadAndExecuteScala(){
  // 1. Validar archivo seleccionado
  const scalaFile = document.getElementById('scalaFile').files[0];
  if(!scalaFile) return alert('Selecciona un archivo .scala');
  if(!scalaFile.name.endsWith('.scala')) 
    return alert('Solo archivos .scala son permitidos');
  
  // 2. Mostrar estado compiling
  const statusEl = document.getElementById('scalaStatus');
  statusEl.textContent = '⏳ Compilando y ejecutando...';
  
  // 3. Construir FormData con archivos
  const form = new FormData();
  form.append('scalaFile', scalaFile);
  
  const config = document.getElementById('scalaConfig').files[0];
  if(config) form.append('configYaml', config);
  
  const mode = document.getElementById('simulationMode').value;
  form.append('simulationMode', mode);
  
  // 4. POST a /api/upload-and-execute
  const r = await fetch(CTX + '/api/upload-and-execute', 
                        {method: 'POST', body: form});
  const j = await r.json();
  
  // 5. Manejar respuesta
  if(!r.ok) {
    statusEl.textContent = '❌ ' + (j.error || j.message);
    return alert(j.error || j.message);
  }
  
  // 6. Actualizar UI con ejecución
  executionId = j.executionId;
  document.getElementById('executionId').textContent = executionId;
  statusEl.textContent = '✅ Compilado. Ejecutando: ' + j.simulationClass;
  
  // 7. Iniciar stream de logs + polling
  document.getElementById('status').textContent = 'RUNNING';
  stream();
  poll();
}
```

---

## 📊 Estados Mostrados

La UI mostrará estados en tiempo real:

```
Antes de subir:
  Estado: -

Compilando:
  Estado: ⏳ Compilando y ejecutando...

Compilado y ejecutando:
  Estado: ✅ Compilado. Ejecutando: bci.cards.simulation.DemoSimulation

Error de compilación:
  Estado: ❌ Contenido peligroso detectado: System.exit

Error de archivo:
  Estado: ❌ Compilación Scala fallida (exit code: 1)
```

---

## 🔗 Integración con Ejecución

Después de compilar exitosamente:

1. **Auto-genera `executionId`** en sección "3. Ejecutar"
2. **Inicia stream de logs** en "5. Log en vivo"
3. **Polling automático** cada 2s para actualizar estado
4. **Botón "Descargar reporte"** disponible cuando complete

```javascript
// Estos se ejecutan automáticamente:
stream();   // EventSource a /api/executions/{id}/logs/stream
poll();     // Fetch a /api/executions/{id} cada 2s
```

---

## 💡 Flujo Completo Usuario

### Paso 1: Preparar archivo Scala

**DemoSimulation.scala**:
```scala
class DemoSimulation extends Simulation {
  val httpProtocol = http.baseUrl("https://jsonplaceholder.typicode.com")
  
  val scn = scenario("Demo")
    .exec(http("GET /posts").get("/posts").check(status.is(200)))
  
  setUp(scn.inject(atOnceUsers(5))).protocols(httpProtocol)
}
```

### Paso 2: Abrir UI

```
http://localhost:8080/
```

### Paso 3: Upload en sección 1b

1. Click "Archivo .scala" → Seleccionar `DemoSimulation.scala`
2. (Opcional) Click "Configuración YAML" → Seleccionar config
3. Seleccionar modo: `PEAK`
4. Click botón verde "Compilar y Ejecutar"

### Paso 4: Monitor en vivo

- Sección "5. Log en vivo" mostrará:
  ```
  ⏳ Compiling Scala...
  ✅ Compilation successful
  Starting Gatling simulation...
  [Gatling output...]
  Simulation completed
  ```

- Sección "3. Ejecutar" mostrará:
  ```
  Estado: RUNNING
  (después de 14 minutos)
  Estado: SUCCESS
  ```

### Paso 5: Descargar reporte

Click "Descargar reporte" → HTML con gráficos Gatling

---

## 🎨 Estilos CSS

### Botón "Compilar y Ejecutar"

```css
button {
  background: #2563eb;  /* Azul default */
  color: white;
  border: 0;
  border-radius: 8px;
  padding: 10px 14px;
  cursor: pointer;
}

/* Botón especial para Scala */
.scala-button {
  background: #10b981;  /* Verde */
}
```

### Estado Texto

```css
.status {
  font-weight: 700;    /* Bold */
  color: inherit;      /* Hereda color del párrafo */
}

/* Ejemplos */
p { color: #17202a; }  /* Gris oscuro default */

/* Estados actualizan dinámicamente */
document.getElementById('scalaStatus').textContent = '✅ Compilado';
```

---

## 🔒 Validaciones Frontend

| Validación | Mensaje | Acción |
|-----------|---------|--------|
| Archivo no seleccionado | "Selecciona un archivo .scala" | Alert + No envía |
| Archivo no es .scala | "Solo archivos .scala son permitidos" | Alert + No envía |
| Request falla (500) | "Error: " + mensaje del servidor | Alert + Mostrar estado |
| Falta scalaFile en response | "Clase no encontrada" | Alert + Mostrar estado |

---

## 📱 Responsividad

El grid CSS es responsive:

```css
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;
}

/* En pantalla mobile: 1 columna */
/* En pantalla tablet: 2-3 columnas */
/* En pantalla desktop: 4 columnas */
```

Cada card tiene:
- Ancho mínimo: 280px
- Padding: 18px
- Border-radius: 12px
- Box-shadow: 0 2px 10px rgba(0,0,0,0.1)

---

## 🧪 Testing Manual

### Test 1: Validación archivo no seleccionado

```
1. No seleccionar archivo
2. Click "Compilar y Ejecutar"
3. ✅ Alert: "Selecciona un archivo .scala"
```

### Test 2: Archivo válido

```
1. Seleccionar DemoSimulation.scala
2. Modo: PEAK
3. Click "Compilar y Ejecutar"
4. ✅ Estado: "⏳ Compilando y ejecutando..."
5. (después de ~10s)
6. ✅ Estado: "✅ Compilado. Ejecutando: bci.cards.simulation.DemoSimulation"
7. Logs en vivo aparecen
```

### Test 3: Archivo con contenido malicioso

```
1. Crear archivo: Malicious.scala con "System.exit(0)"
2. Upload
3. ✅ Error: "❌ Contenido peligroso detectado: System.exit"
```

---

## 📚 Referencia Rápida

```html
<!-- Input: Archivo Scala -->
<input id="scalaFile" type="file" accept=".scala">

<!-- Select: Modo simulación -->
<select id="simulationMode">
  <option value="LOAD">LOAD</option>
  <option value="PEAK">PEAK</option>
  <option value="SMOKE">SMOKE</option>
</select>

<!-- Botón: Disparar compilación -->
<button onclick="uploadAndExecuteScala()" style="background:#10b981">
  Compilar y Ejecutar
</button>

<!-- Estado: Mostrar progreso -->
<p>Estado: <span id="scalaStatus" class="status">-</span></p>
```

---

## 🚀 Próximas Mejoras Sugeridas

1. **Syntax Highlighting**: Editor Ace/Monaco para escribir Scala directo
2. **Templates**: Botones rápidos con ejemplos (Login, JSON API, etc.)
3. **History**: Tabla de ejecuciones pasadas
4. **Comparison**: Comparar reportes de dos ejecuciones
5. **Real-time Metrics**: Gráfico de TPS/Latencia mientras ejecuta

---

**Implementación**: ✅ COMPLETA  
**Testing**: ✅ Manual OK  
**Documentación**: ✅ Completa  

package cl.bci.vaultchecker.performance;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.Instant;

@RestController
@RequestMapping("/api/performance")
public class PerformanceController {

    private final PerformanceExecutionService service;

    public PerformanceController(PerformanceExecutionService service) {
        this.service = service;
    }

    @PostMapping(value = "/simulations/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> validate(@RequestParam("file") MultipartFile file) throws Exception {
        return noStore(service.validateOnly(file));
    }

    @PostMapping(value = "/simulations", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) throws Exception {
        return noStore(service.upload(file));
    }

    @GetMapping("/simulations")
    public ResponseEntity<List<Map<String, Object>>> simulations() throws Exception {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.listSimulations());
    }

    @GetMapping("/runtime")
    public ResponseEntity<Map<String, Object>> runtime() {
        return noStore(service.runtimeInfo());
    }

    @PostMapping("/executions")
    public ResponseEntity<Map<String, Object>> start(@RequestParam("simulationClass") String simulationClass)
            throws Exception {
        return noStore(service.start(simulationClass));
    }

    @GetMapping("/executions")
    public ResponseEntity<List<Map<String, Object>>> executions() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.listExecutions());
    }

    @GetMapping("/executions/latest")
    public ResponseEntity<Map<String, Object>> latestExecutionLog() {
        return noStore(service.latestExecutionLog());
    }

    @GetMapping("/executions/{id}")
    public ResponseEntity<Map<String, Object>> status(@PathVariable String id) {
        return noStore(service.status(id));
    }

    @GetMapping(value = "/executions/{id}/logs", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> logs(@PathVariable String id,
                                                     @RequestParam(defaultValue = "0") long offset) throws Exception {
        String content = service.logs(id, offset);
        Map<String, Object> status = service.status(id);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", content);
        response.put("next_offset", service.logSize(id));
        response.put("execution_id", id);
        response.put("status", status.get("status"));
        response.put("error_type", status.get("error_type"));
        response.put("error_summary", status.get("error_summary"));
        response.put("error_at", status.get("error_at"));
        return noStore(response);
    }

    @PostMapping("/executions/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable String id) {
        return noStore(service.cancel(id));
    }

    @GetMapping("/executions/{id}/results")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable String id) {
        StreamingResponseBody body = outputStream -> service.zipResults(id, outputStream);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=gatling-results-" + id + ".zip")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(RuntimeException ex) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ERROR");
        response.put("message", ex.getMessage());
        response.put("error_type", ex.getClass().getSimpleName());
        response.put("timestamp", Instant.now().toString());
        response.put("hint", resolveHint(ex.getMessage()));
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(response);
    }

    private String resolveHint(String message) {
        if (message == null || message.isBlank()) {
            return "Revisa los logs del backend y vuelve a intentar.";
        }
        if (message.contains("Ejecución no encontrada")) {
            return "El pod pudo reiniciarse. Consulta /api/performance/executions/latest para recuperar el último log persistido.";
        }
        if (message.contains("Ya existe una ejecución activa")) {
            return "Adjúntate a la ejecución activa desde /api/performance/executions o espera su finalización.";
        }
        if (message.contains("Clase de simulación inválida")) {
            return "Verifica que la clase cumpla el patrón bci.cards.simulation.NombreClase.";
        }
        return "Revisa execution.log y estado de /api/performance/runtime para más detalle.";
    }

    private ResponseEntity<Map<String, Object>> noStore(Map<String, Object> body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}

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

    @GetMapping("/executions/{id}")
    public ResponseEntity<Map<String, Object>> status(@PathVariable String id) {
        return noStore(service.status(id));
    }

    @GetMapping(value = "/executions/{id}/logs", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> logs(@PathVariable String id,
                                                     @RequestParam(defaultValue = "0") long offset) throws Exception {
        String content = service.logs(id, offset);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", content);
        response.put("next_offset", service.logSize(id));
        return noStore(response);
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
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(response);
    }

    private ResponseEntity<Map<String, Object>> noStore(Map<String, Object> body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}

package cl.bci.vaultchecker.security;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/security")
public class SecurityController {

    private final SecurityScanService service;

    public SecurityController(SecurityScanService service) {
        this.service = service;
    }

    @PostMapping("/scan")
    public ResponseEntity<Map<String, Object>> scan() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.start());
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.status());
    }

    @GetMapping("/findings")
    public ResponseEntity<Map<String, Object>> findings() throws Exception {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.findings());
    }

    @GetMapping(value = "/logs", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> logs() throws Exception {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.logs());
    }

    @GetMapping("/reports")
    public ResponseEntity<StreamingResponseBody> reports() {
        StreamingResponseBody body = service::zipReports;
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=owasp-dependency-check-reports.zip")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, Object>> handle(RuntimeException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ERROR");
        body.put("message", ex.getMessage());
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(body);
    }
}

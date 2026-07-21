package cl.bci.vaultchecker.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/vault-test")
public class VaultDashboardController {

    // CHANGESET-VAULT-CONTROLLER-2026-07-20:
    // Subir este archivo para reflejar endpoint /vault-test/token-pruebas,
    // lectura directa con source/fingerprint y ajustes de rutas Vault.

    private final ObjectProvider<VaultTemplate> vaultTemplateProvider;
    private final Environment environment;

    @Value("${spring.cloud.vault.application-name:token_pruebas}")
    private String vaultKvApplicationName;

    @Value("${spring.profiles.active:qa}")
    private String vaultKvProfiles;

    @Value("${spring.cloud.vault.kv.backend:secret}")
    private String vaultBackend;

    @Value("${VAULT_SECRET_KEY_NAME:token}")
    private String vaultSecretKeyName;

    @Value("${VAULT_ENABLED:true}")
    private boolean vaultEnabled;

    @Value("${VAULT_HOST:vault-server-service.bci-infra}")
    private String vaultHost;

    @Value("${VAULT_PORT:8200}")
    private int vaultPort;

    @Value("${VAULT_TRUST_STORE:}")
    private String vaultTrustStore;

    @Value("${vault.diagnostics.max-events:200}")
    private int maxEvents;

    private final Deque<Map<String, Object>> diagnosticEvents = new ArrayDeque<>();
    private volatile Map<String, Object> lastPrecheck = new LinkedHashMap<>();

    public VaultDashboardController(
            ObjectProvider<VaultTemplate> vaultTemplateProvider,
            Environment environment
    ) {
        this.vaultTemplateProvider = vaultTemplateProvider;
        this.environment = environment;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> probarConexionVault() {
        Map<String, Object> response = new LinkedHashMap<>();
        String secretoVault = environment.getProperty(vaultSecretKeyName);
        boolean found = secretoVault != null && !secretoVault.isBlank();
        boolean localMock = !vaultEnabled && found;
        String status = localMock ? "LOCAL_MOCK" : found ? "SUCCESS" : vaultEnabled ? "NOT_FOUND" : "DISABLED";
        String source = localMock ? "LOCAL_ENVIRONMENT" : found ? "VAULT" : "NONE";
        response.put("status", status);
        response.put("mensaje", localMock
                ? "Credencial ficticia cargada desde el entorno local; Vault está deshabilitado"
                : found
                    ? "Secreto cargado por Spring desde Vault"
                    : "Spring no encontró la clave configurada '" + vaultSecretKeyName + "'");
        response.put("source", source);
        response.put("vault_enabled", vaultEnabled);
        response.put("key_name", vaultSecretKeyName);
        response.put("valor_configurado", localMock ? "LOCAL_MOCK_ONLY" : maskSecret(secretoVault));

        return noStoreResponse(response);
    }

    @GetMapping("/hola-mundo")
    public ResponseEntity<Map<String, Object>> holaMundo() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "SUCCESS");
        response.put("mensaje", "hola mundo");
        response.put("endpoint", "/vault-test/hola-mundo");
        return noStoreResponse(response);
    }

    @GetMapping("/precheck")
    public ResponseEntity<Map<String, Object>> precheckVault() {
        return noStoreResponse(runPrecheck(true));
    }

    @Scheduled(fixedDelayString = "${vault.diagnostics.precheck-interval-ms:60000}")
    public void precheckProgramado() {
        runPrecheck(false);
    }

    @GetMapping("/alerts")
    public ResponseEntity<Map<String, Object>> obtenerAlertas(@RequestParam(defaultValue = "false") boolean refresh) {
        Map<String, Object> snapshot = refresh ? runPrecheck(true) : ensurePrecheckSnapshot();
        List<Map<String, Object>> failedItems = collectFailedItems(snapshot);
        Map<String, Object> response = new LinkedHashMap<>();
        String status = String.valueOf(snapshot.getOrDefault("status", "UNKNOWN"));
        int failedChecks = ((Number) snapshot.getOrDefault("failed_checks", 0)).intValue();
        response.put("status", status);
        response.put("failed_checks", failedChecks);
        response.put("severity", inferSeverity(status, failedChecks));
        response.put("failed_items", failedItems);
        response.put("probable_root_cause", inferProbableRootCause(failedItems));
        response.put("last_precheck_at", snapshot.get("timestamp"));
        return noStoreResponse(response);
    }

    @GetMapping("/events")
    public ResponseEntity<Map<String, Object>> obtenerEventos(@RequestParam(defaultValue = "40") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, maxEvents));
        List<Map<String, Object>> events;
        synchronized (diagnosticEvents) {
            events = diagnosticEvents.stream().limit(safeLimit).collect(Collectors.toList());
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("count", events.size());
        response.put("limit", safeLimit);
        response.put("events", events);
        return noStoreResponse(response);
    }

    private ResponseEntity<Map<String, Object>> noStoreResponse(Map<String, Object> body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .body(body);
    }

    private Map<String, Object> runPrecheck(boolean recordSuccessEvent) {
        Map<String, Object> response = new LinkedHashMap<>();
        List<Map<String, Object>> checks = new ArrayList<>();

        response.put("timestamp", Instant.now().toString());
        response.put("vault_enabled", vaultEnabled);
        response.put("vault_host", vaultHost);
        response.put("vault_port", vaultPort);
        response.put("backend", vaultBackend);
        String requestedPath = buildRequestedPath();
        response.put("requested_path", requestedPath);

        addCheck(checks, "vault_enabled", vaultEnabled, vaultEnabled ? "Vault habilitado" : "Vault deshabilitado por configuración");

        boolean dnsOk = false;
        String dnsMessage;
        try {
            dnsMessage = InetAddress.getByName(vaultHost).getHostAddress();
            dnsOk = true;
        } catch (Exception ex) {
            dnsMessage = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        }
        addCheck(checks, "dns_resolution", dnsOk, dnsMessage);

        boolean tcpOk = false;
        String tcpMessage;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(vaultHost, vaultPort), 1500);
            tcpOk = true;
            tcpMessage = "Conexión TCP exitosa";
        } catch (Exception ex) {
            tcpMessage = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        }
        addCheck(checks, "tcp_connectivity", tcpOk, tcpMessage);

        boolean trustStoreOk = false;
        String trustStoreMessage = "No configurado";
        if (vaultTrustStore != null && vaultTrustStore.startsWith("file:")) {
            File file = new File(vaultTrustStore.substring("file:".length()));
            trustStoreOk = file.exists() && file.isFile();
            trustStoreMessage = trustStoreOk ? "Archivo encontrado" : "Archivo no encontrado";
        }
        addCheck(checks, "trust_store_file", trustStoreOk, trustStoreMessage);

        VaultTemplate vaultTemplate = vaultTemplateProvider.getIfAvailable();
        boolean templateOk = vaultTemplate != null;
        addCheck(
                checks,
                "vault_template_bean",
                templateOk,
                templateOk ? "Bean de Vault disponible" : "Bean no disponible. Revisar SPRING_CLOUD_VAULT_ENABLED/VAULT_ENABLED"
        );

        if (templateOk && vaultEnabled) {
            try {
                String secretValue = environment.getProperty(vaultSecretKeyName);
                boolean found = secretValue != null && !secretValue.isBlank();
                addCheck(
                        checks,
                        "vault_secret_read",
                        found,
                        found
                                ? "Propiedad cargada por Spring. key=" + vaultSecretKeyName
                                : "Spring no cargó la clave configurada. Verificar nombre, ruta y permisos AppRole"
                );
                response.put("normalized_path", vaultKvApplicationName + "/" + vaultKvProfiles);
                response.put("keys", found ? List.of(vaultSecretKeyName) : List.of());
                response.put("values_count", found ? 1 : 0);
            } catch (Exception ex) {
                addCheck(
                        checks,
                        "vault_secret_read",
                        false,
                        ex.getClass().getSimpleName() + ": " + ex.getMessage()
                );
            }
        }

        long failed = checks.stream().filter(item -> !(Boolean) item.get("ok")).count();
        response.put("status", failed == 0 ? "READY" : "NOT_READY");
        response.put("failed_checks", failed);
        response.put("checks", checks);
        lastPrecheck = response;

        if (failed == 0) {
            if (recordSuccessEvent) {
                recordEvent("INFO", "VAULT_PRECHECK_READY", "Precheck completado sin fallas", response);
            }
        } else {
            recordEvent("WARN", "VAULT_PRECHECK_NOT_READY", "Precheck con fallas", response);
        }

        return response;
    }

    private Map<String, Object> ensurePrecheckSnapshot() {
        if (lastPrecheck.isEmpty()) {
            return runPrecheck(false);
        }
        return lastPrecheck;
    }

    private List<Map<String, Object>> collectFailedItems(Map<String, Object> snapshot) {
        Object checksObj = snapshot.get("checks");
        if (!(checksObj instanceof List)) {
            return List.of();
        }
        List<?> checks = (List<?>) checksObj;

        List<Map<String, Object>> failed = new ArrayList<>();
        for (Object item : checks) {
            if (item instanceof Map) {
                Map<?, ?> raw = (Map<?, ?>) item;
                Object okObj = raw.get("ok");
                if (okObj instanceof Boolean && !(Boolean) okObj) {
                    Map<String, Object> normalized = new LinkedHashMap<>();
                    normalized.put("name", raw.get("name"));
                    normalized.put("detail", raw.get("detail"));
                    failed.add(normalized);
                }
            }
        }
        return failed;
    }

    private String buildRequestedPath() {
        String backend = vaultBackend == null ? "secret" : vaultBackend.trim();
        if (backend.isEmpty()) {
            backend = "secret";
        }

        String appName = vaultKvApplicationName == null ? "" : vaultKvApplicationName.trim();
        String profiles = vaultKvProfiles == null ? "" : vaultKvProfiles.trim();

        if (appName.isEmpty()) {
            appName = "token_pruebas";
        }

        String firstProfile = "qa";
        if (!profiles.isEmpty()) {
            firstProfile = profiles.split(",")[0].trim();
            if (firstProfile.isEmpty()) {
                firstProfile = "qa";
            }
        }

        return backend + "/" + appName + "/" + firstProfile;
    }

    private void addCheck(List<Map<String, Object>> checks, String name, boolean ok, String detail) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("ok", ok);
        item.put("detail", detail);
        checks.add(item);
    }

    private String inferSeverity(String status, int failedChecks) {
        if ("READY".equalsIgnoreCase(status) && failedChecks == 0) {
            return "GREEN";
        }
        if (failedChecks <= 2) {
            return "YELLOW";
        }
        return "RED";
    }

    private String inferProbableRootCause(List<Map<String, Object>> failedItems) {
        for (Map<String, Object> failed : failedItems) {
            String name = String.valueOf(failed.getOrDefault("name", ""));
            String detail = String.valueOf(failed.getOrDefault("detail", ""));
            String detailLower = detail.toLowerCase(Locale.ROOT);

            if ("vault_enabled".equals(name)) {
                return "Vault esta deshabilitado por configuracion (VAULT_ENABLED o SPRING_CLOUD_VAULT_ENABLED).";
            }
            if ("dns_resolution".equals(name)) {
                return "El host de Vault no resuelve por DNS desde el pod/entorno. Revisar VAULT_HOST y red del cluster.";
            }
            if ("tcp_connectivity".equals(name)) {
                return "No hay conectividad TCP al puerto de Vault. Revisar firewall, network policy o ruta.";
            }
            if ("trust_store_file".equals(name)) {
                return "No se encontro el truststore configurado. Revisar secret montado en /vol-ms y VAULT_TRUST_STORE.";
            }
            if ("vault_template_bean".equals(name)) {
                return "Spring no creo VaultTemplate. Revisar flags de habilitacion y bootstrap de Spring Cloud Vault.";
            }
            if ("vault_secret_read".equals(name)) {
                if (detailLower.contains("permission") || detailLower.contains("forbidden") || detailLower.contains("403")) {
                    return "La autenticacion funciona pero la policy no permite leer el path solicitado en Vault.";
                }
                if (detailLower.contains("unknownhost") || detailLower.contains("connection") || detailLower.contains("timeout")) {
                    return "Falla de red hacia Vault durante login/lectura. Revisar DNS, conectividad y endpoint.";
                }
                return "Fallo al leer secreto en Vault. Revisar AppRole (role_id/secret_id), policy y ruta KV v2.";
            }
        }
        return failedItems.isEmpty()
                ? "Sin fallas detectadas. Integracion Vault operativa."
                : "No fue posible inferir una causa unica. Revisar checks fallidos.";
    }

    private String maskSecret(String value) {
        if (value == null || value.isBlank()) {
            return "<vacío>";
        }

        String trimmed = value.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }

        int visiblePrefix = Math.min(3, trimmed.length() - 2);
        int visibleSuffix = Math.min(3, trimmed.length() - visiblePrefix);
        String prefix = trimmed.substring(0, visiblePrefix);
        String suffix = trimmed.substring(trimmed.length() - visibleSuffix);
        StringBuilder masked = new StringBuilder(prefix);
        for (int i = 0; i < trimmed.length() - visiblePrefix - visibleSuffix; i++) {
            masked.append('*');
        }
        masked.append(suffix);
        return masked.toString();
    }

    private void recordEvent(String level, String code, String message, Map<String, Object> payload) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("timestamp", Instant.now().toString());
        event.put("level", level);
        event.put("code", code);
        event.put("message", message);
        event.put("payload", payload);

        synchronized (diagnosticEvents) {
            diagnosticEvents.addFirst(event);
            while (diagnosticEvents.size() > maxEvents) {
                diagnosticEvents.removeLast();
            }
        }
    }
}

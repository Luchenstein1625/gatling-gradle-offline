package cl.bci.performance.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import cl.bci.performance.api.model.ExecutionInfo;
import cl.bci.performance.api.service.*;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.Objects;
import java.util.HashMap;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final StorageService storage;
    private final ExecutionService executions;
    private final SystemInfoService systemInfo;
    private final KubernetesLogService kubernetesLogs;
    private final SimulationCompilerService compiler;  // NEW: Dynamic Scala compilation
    private final ConfigurableEnvironment environment;  // NEW: Vault configuration access
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public ApiController(
            StorageService storage,
            ExecutionService executions,
            SystemInfoService systemInfo,
            KubernetesLogService kubernetesLogs,
            SimulationCompilerService compiler,
            ConfigurableEnvironment environment) {  // NEW: Inject compiler service & vault config
        this.storage = storage;
        this.executions = executions;
        this.systemInfo = systemInfo;
        this.kubernetesLogs = kubernetesLogs;
        this.compiler = compiler;  // NEW
        this.environment = environment;  // NEW
    }

    @Operation(summary = "Descargar una plantilla YAML")
    @GetMapping("/templates/{name}")
    public ResponseEntity<Resource> template(@PathVariable String name) {
        String allowed = switch (name) {
            case "peak-login", "tps-login", "peak-external-token", "tps-external-token",
                    "mock-jsonplaceholder", "mock-httpbin" -> name;
            default -> throw new IllegalArgumentException("Plantilla no permitida");
        };

        Resource resource = new ClassPathResource("templates/" + allowed + ".yaml");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + allowed + ".yaml\"")
                .contentType(MediaType.parseMediaType("application/yaml"))
                .body(resource);
    }


    @Operation(summary = "Descargar plantilla CSV de usuarios")
    @GetMapping("/templates/users-csv")
    public ResponseEntity<Resource> usersTemplate() {
        Resource resource = new ClassPathResource("templates/users-template.csv");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"users-template.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(resource);
    }

    @Operation(summary = "Subir configuración YAML y CSV opcional")
    @PostMapping(value = "/configurations", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> upload(
            @RequestPart("configuration") MultipartFile configuration,
            @RequestPart(value = "users", required = false) MultipartFile users) throws IOException {

        if (configuration.isEmpty()) {
            throw new IllegalArgumentException("configuration es obligatorio");
        }

        String simulationClass = readSimulationClass(configuration);

        boolean requiresUsersCsv =
                "bci.cards.simulation.ConfigurableBcimsSimulation".equals(simulationClass);

        if (requiresUsersCsv && (users == null || users.isEmpty())) {
            throw new IllegalArgumentException(
                    "users.csv es obligatorio para ConfigurableBcimsSimulation"
            );
        }

        String id = storage.saveConfiguration(configuration, users);

        return Map.of(
                "configurationId", id,
                "simulationClass", simulationClass,
                "usersCsvRequired", Boolean.toString(requiresUsersCsv),
                "status", "VALIDATED"
        );
    }

    /**
     * 🚀 ENDPOINT: Subir archivo Scala, compilar en runtime, y ejecutar automáticamente
     * 
     * ⛔ IMPORTANTE: Esta compilación sucede DENTRO de Docker.
     * NO ejecutar ./gradlew compileGatlingScala en local para probar Scala dinámico.
     * Ver: .NO-LOCAL-GRADLE-SCALA (marcador en raíz del proyecto)
     * 
     * Workflow:
     * 1. Compilar archivo Scala (con logs en vivo, se muestran en "5. Log en vivo")
     * 2. Validar clase compilada
     * 3. Crear configuración con la clase compilada
     * 4. Iniciar simulación automáticamente
     * 
     * Request:
     *   - scalaFile: Archivo .scala (ej: CustomSimulation.scala)
     *   - configYaml: Configuración YAML opcional
     *   - simulationMode: "SMOKE", "LOAD", "PEAK" (default: "LOAD")
     * 
     * Response: { executionId, className, status: "RUNNING" }
     */
    @Operation(summary = "Upload Scala file, compile, and execute")
    @PostMapping(value = "/upload-and-execute", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadAndExecute(
            @RequestPart("scalaFile") MultipartFile scalaFile,
            @RequestPart(value = "configYaml", required = false) MultipartFile configYaml,
            @RequestParam(value = "simulationMode", defaultValue = "LOAD") String simulationMode) 
            throws IOException, InterruptedException, ClassNotFoundException {
        
        try {
            // Compilar archivo Scala (logs van a stdout y se ven en "5. Log en vivo")
            String className = compiler.compileScalaFile(
                scalaFile.getOriginalFilename(),
                new String(scalaFile.getBytes()),
                null  // Sin execution ID aún (compilación solo)
            );
            
            // Validar clase compilada en classpath
            compiler.validateCompiledClass(className);
            
            // Crear configuración con la clase compilada
            String configurationId = createTemporaryConfiguration(
                className, 
                simulationMode, 
                configYaml
            );
            
            // Ejecutar simulación automáticamente
            String executionId = executions.start(configurationId);
            
            return ResponseEntity.ok(Map.of(
                "executionId", executionId,
                "simulationClass", className,
                "configurationId", configurationId,
                "status", "RUNNING",
                "message", "Scala compilado y simulación iniciada"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Compilación fallida",
                "message", e.getMessage()
            ));
        }
    }

    private String createTemporaryConfiguration(String simulationClass, String mode, MultipartFile configYaml) throws IOException {
        String config;
        
        if (configYaml != null && !configYaml.isEmpty()) {
            config = new String(configYaml.getBytes());
        } else {
            // Crear config minimal por defecto
            config = String.format("""
                name: Custom Simulation Runtime Upload
                simulationClass: %s
                mode: %s
                injection:
                  atOnceUsers: 1
                  duration: 30 seconds
                sla:
                  maxResponseTime: 3000
                  failedRequests: 0
                """, simulationClass, mode);
        }

        return storage.saveConfigurationFromString(config, null);
    }

    @SuppressWarnings("unchecked")
    private String readSimulationClass(MultipartFile configuration) throws IOException {
        try (InputStream input = configuration.getInputStream()) {
            Map<String, Object> yaml = yamlMapper.readValue(input, Map.class);

            return Objects.toString(
                    yaml.getOrDefault(
                            "simulationClass",
                            "bci.cards.simulation.ConfigurableBcimsSimulation"
                    )
            );
        }
    }

    @Operation(summary = "Ejecutar una configuración")
    @PostMapping("/executions/{configurationId}")
    public ResponseEntity<Map<String, String>> execute(
            @PathVariable String configurationId) throws IOException {
        String id = executions.start(configurationId);
        return ResponseEntity.accepted()
                .body(Map.of("executionId", id, "status", "QUEUED"));
    }

    @Operation(summary = "Consultar estado")
    @GetMapping("/executions/{executionId}")
    public ExecutionInfo status(@PathVariable String executionId) {
        return executions.get(executionId);
    }

    @Operation(summary = "Seguir log en vivo por SSE")
    @GetMapping(value = "/executions/{executionId}/logs/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String executionId) {
        return executions.stream(executionId);
    }

    @Operation(summary = "Descargar log")
    @GetMapping("/executions/{executionId}/logs")
    public ResponseEntity<Resource> log(@PathVariable String executionId) {
        return file(executions.log(executionId), "execution.log", MediaType.TEXT_PLAIN);
    }

    @Operation(summary = "Descargar reporte ZIP")
    @GetMapping("/executions/{executionId}/report")
    public ResponseEntity<Resource> report(@PathVariable String executionId) {
        return file(executions.report(executionId), "gatling-report.zip",
                MediaType.APPLICATION_OCTET_STREAM);
    }

    @Operation(summary = "Recursos JVM, cgroups y pod")
    @GetMapping("/system/resources")
    public Map<String, Object> resources() {
        return systemInfo.resources();
    }

    @Operation(summary = "Últimas líneas del log del propio pod; requiere RBAC pods/log")
    @GetMapping(value = "/kubernetes/pod/logs", produces = MediaType.TEXT_PLAIN_VALUE)
    public String podLogs() throws Exception {
        return kubernetesLogs.ownPodLogs();
    }

    @Operation(summary = "Validar conectividad a Vault y obtener estado del token (enmascarado)")
    @GetMapping("/vault/status")
    public Map<String, Object> vaultStatus() {
        Map<String, Object> response = new HashMap<>();

        try {
            String vaultEnabled = environment.getProperty("spring.cloud.vault.enabled");
            boolean isEnabled = "true".equalsIgnoreCase(vaultEnabled);

            if (!isEnabled) {
                response.put("status", "DISABLED");
                response.put("token", "Token No Disponible");
                response.put("tokenAvailable", false);
                response.put("reason", "Vault no habilitado (VAULT_ENABLED no establecido)");
                return response;
            }

            String vaultScheme = environment.getProperty("spring.cloud.vault.scheme");
            String vaultHost = environment.getProperty("spring.cloud.vault.host");
            String vaultPort = environment.getProperty("spring.cloud.vault.port");
            String vaultAuth = environment.getProperty("spring.cloud.vault.authentication");
            String vaultApplicationName = environment.getProperty("spring.cloud.vault.application-name");
            String secretPath = environment.getProperty("spring.cloud.vault.secret-path");

            response.put("status", "CONNECTED");
            response.put("scheme", vaultScheme);
            response.put("host", vaultHost);
            response.put("port", vaultPort != null ? vaultPort : "8200");
            response.put("authentication", vaultAuth != null ? vaultAuth : "APPROLE");
            response.put("applicationName", vaultApplicationName);
            response.put("secretPath", secretPath != null ? secretPath : "secret/");

            // Try to get token from resolved properties (if available via Vault bootstrap)
            try {
                String tokenProperty = environment.getProperty("vault.token");
                if (tokenProperty != null && !tokenProperty.isEmpty()) {
                    String masked = tokenProperty.length() > 8 ?
                            tokenProperty.substring(0, 8) + "***" : "****";
                    response.put("token", masked);
                    response.put("tokenAvailable", true);
                } else {
                    response.put("token", "Token No Disponible");
                    response.put("tokenAvailable", false);
                }
            } catch (Exception e) {
                response.put("token", "Token No Disponible");
                response.put("tokenAvailable", false);
            }
        } catch (Exception e) {
            response.put("status", "ERROR");
            response.put("token", "Token No Disponible");
            response.put("tokenAvailable", false);
            response.put("error", e.getMessage());
        }

        return response;
    }

    private ResponseEntity<Resource> file(Path path, String filename, MediaType type) {
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(path);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(type)
                .body(resource);
    }
}

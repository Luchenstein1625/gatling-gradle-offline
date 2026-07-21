package cl.bci.vaultchecker.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class SecurityScanService {

    private final ObjectMapper objectMapper;
    private final String gradleCommand;
    private final String trivyCommand;
    private final Path projectDir;
    private final Path reportDir;
    private final Path logFile;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile String status = "NOT_RUN";
    private volatile Instant startedAt;
    private volatile Instant finishedAt;
    private volatile Integer exitCode;
    private volatile Integer owaspExitCode;
    private volatile Integer trivyExitCode;
    private volatile Process process;

    public SecurityScanService(
            ObjectMapper objectMapper,
            @Value("${security.scan.gradle-command:${GATLING_COMMAND:/opt/gradle/gradle-8.6/bin/gradle}}") String gradleCommand,
            @Value("${security.scan.trivy-command:${TRIVY_COMMAND:/usr/local/bin/trivy}}") String trivyCommand,
            @Value("${security.scan.project-dir:${SECURITY_PROJECT_DIR:/app/security-runner}}") String projectDir,
            @Value("${security.scan.report-dir:${SECURITY_REPORT_DIR:/app/data/security/reports}}") String reportDir
    ) throws IOException {
        this.objectMapper = objectMapper;
        this.gradleCommand = gradleCommand;
        this.trivyCommand = trivyCommand;
        this.projectDir = Path.of(projectDir).toAbsolutePath().normalize();
        this.reportDir = Path.of(reportDir).toAbsolutePath().normalize();
        this.logFile = this.reportDir.resolve("security-scan.log");
        Files.createDirectories(this.reportDir);
        if (Files.isRegularFile(jsonReport()) || Files.isRegularFile(trivyReport())) {
            status = "COMPLETED";
        }
    }

    public synchronized Map<String, Object> start() {
        if ("RUNNING".equals(status)) {
            throw new IllegalStateException("Ya existe un análisis de seguridad en ejecución.");
        }
        if (!Files.isExecutable(Path.of(gradleCommand))) {
            throw new IllegalStateException("No existe el ejecutable Gradle: " + gradleCommand);
        }
        if (!Files.isRegularFile(projectDir.resolve("build.gradle"))) {
            throw new IllegalStateException("No existe el proyecto OWASP Dependency-Check: " + projectDir);
        }
        if (!Files.isExecutable(Path.of(trivyCommand))) {
            throw new IllegalStateException("No existe el ejecutable Trivy: " + trivyCommand);
        }
        status = "QUEUED";
        startedAt = Instant.now();
        finishedAt = null;
        exitCode = null;
        owaspExitCode = null;
        trivyExitCode = null;
        executor.submit(this::run);
        return status();
    }

    private void run() {
        status = "RUNNING";
        try {
            Files.createDirectories(reportDir);
            Files.writeString(logFile,
                    "[SECURITY] Análisis cruzado: OWASP Dependency-Check 12.2.2 + Trivy 0.72.0" + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            appendLog("[OWASP] Iniciando análisis de app.jar");
            owaspExitCode = execute(List.of(
                    gradleCommand, "--no-daemon", "--console=plain", "-p", projectDir.toString(),
                    "dependencyCheckAnalyze"
            ), projectDir, "OWASP");

            appendLog("[TRIVY] Iniciando análisis del filesystem, sistema operativo y dependencias de analizadores");
            trivyExitCode = execute(List.of(
                    trivyCommand, "rootfs", "--scanners", "vuln", "--severity", "CRITICAL,HIGH,MEDIUM",
                    "--format", "json", "--output", trivyReport().toString(),
                    "--skip-dirs", "/proc", "--skip-dirs", "/sys", "--skip-dirs", "/dev",
                    "--skip-dirs", "/app/data", "/"
            ), projectDir, "TRIVY");

            boolean owaspOk = owaspExitCode == 0 && Files.isRegularFile(jsonReport());
            boolean trivyOk = trivyExitCode == 0 && Files.isRegularFile(trivyReport());
            exitCode = owaspOk && trivyOk ? 0 : 1;
            status = owaspOk && trivyOk ? "COMPLETED" : (owaspOk || trivyOk ? "PARTIAL" : "FAILED");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            exitCode = -1;
            status = "FAILED";
        } catch (Exception ex) {
            exitCode = -1;
            status = "FAILED";
            try {
                Files.writeString(logFile, "[SECURITY] ERROR: " + ex.getMessage() + System.lineSeparator(),
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ignored) {
                // El estado FAILED sigue disponible aunque no se pueda escribir el log.
            }
        } finally {
            process = null;
            finishedAt = Instant.now();
        }
    }

    private int execute(List<String> command, Path workingDirectory, String prefix)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDirectory.toFile());
        builder.redirectErrorStream(true);
        process = builder.start();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) appendLog("[" + prefix + "] " + line);
        }
        return process.waitFor();
    }

    private void appendLog(String line) throws IOException {
        Files.writeString(logFile, line + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("scanners", List.of("OWASP Dependency-Check 12.2.2", "Trivy 0.72.0"));
        result.put("started_at", startedAt);
        result.put("finished_at", finishedAt);
        result.put("exit_code", exitCode);
        result.put("owasp_exit_code", owaspExitCode);
        result.put("trivy_exit_code", trivyExitCode);
        result.put("owasp_report_available", Files.isRegularFile(jsonReport()));
        result.put("trivy_report_available", Files.isRegularFile(trivyReport()));
        result.put("report_available", Files.isRegularFile(jsonReport()) || Files.isRegularFile(trivyReport()));
        result.put("first_run_notice", "La primera actualización de NVD y de la base Trivy puede tardar varios minutos.");
        return result;
    }

    public Map<String, Object> findings() throws IOException {
        Map<String, Object> response = status();
        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("CRITICAL", 0);
        counts.put("HIGH", 0);
        counts.put("MEDIUM", 0);

        if (Files.isRegularFile(jsonReport())) {
            JsonNode root = objectMapper.readTree(jsonReport().toFile());
            for (JsonNode dependency : root.path("dependencies")) {
                String component = dependency.path("fileName").asText("Componente desconocido");
                for (JsonNode vulnerability : dependency.path("vulnerabilities")) {
                    double score = score(vulnerability);
                    String severity = severity(vulnerability.path("severity").asText(""), score);
                    if (!counts.containsKey(severity)) {
                        continue;
                    }
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("scanner", "OWASP");
                    item.put("severity", severity);
                    item.put("score", score);
                    item.put("cve", vulnerability.path("name").asText("SIN-ID"));
                    item.put("component", component);
                    item.put("description", vulnerability.path("description").asText("Sin descripción disponible"));
                    item.put("reference", firstReference(vulnerability));
                    item.put("recommendation", "Confirmar versión corregida en la referencia oficial y validar compatibilidad antes de actualizar.");
                    items.add(item);
                    counts.put(severity, counts.get(severity) + 1);
                }
            }
        }

        addTrivyFindings(items, counts);

        items.sort(Comparator
                .comparingInt((Map<String, Object> item) -> severityOrder(String.valueOf(item.get("severity"))))
                .thenComparing((Map<String, Object> item) -> ((Number) item.get("score")).doubleValue(), Comparator.reverseOrder())
                .thenComparing(item -> String.valueOf(item.get("cve"))));
        response.put("counts", counts);
        response.put("total_visible", items.size());
        response.put("findings", items);
        return response;
    }

    public String logs() throws IOException {
        return Files.isRegularFile(logFile) ? Files.readString(logFile, StandardCharsets.UTF_8) : "";
    }

    public void zipReports(OutputStream output) throws IOException {
        if (!Files.isRegularFile(jsonReport()) && !Files.isRegularFile(trivyReport())) {
            throw new IllegalStateException("Todavía no existe un reporte de seguridad.");
        }
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8);
             var paths = Files.walk(reportDir)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().collect(Collectors.toList())) {
                zip.putNextEntry(new ZipEntry(reportDir.relativize(path).toString().replace('\\', '/')));
                Files.copy(path, zip);
                zip.closeEntry();
            }
        }
    }

    private Path jsonReport() {
        return reportDir.resolve("dependency-check-report.json");
    }

    private Path trivyReport() {
        return reportDir.resolve("trivy-report.json");
    }

    private void addTrivyFindings(List<Map<String, Object>> items, Map<String, Integer> counts) throws IOException {
        if (!Files.isRegularFile(trivyReport())) return;
        JsonNode root = objectMapper.readTree(trivyReport().toFile());
        for (JsonNode result : root.path("Results")) {
            for (JsonNode vulnerability : result.path("Vulnerabilities")) {
                String severity = vulnerability.path("Severity").asText("").toUpperCase(Locale.ROOT);
                if (!counts.containsKey(severity)) continue;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("scanner", "TRIVY");
                item.put("severity", severity);
                item.put("score", trivyScore(vulnerability));
                item.put("cve", vulnerability.path("VulnerabilityID").asText("SIN-ID"));
                item.put("component", vulnerability.path("PkgName").asText("Componente desconocido")
                        + " " + vulnerability.path("InstalledVersion").asText(""));
                item.put("fixed_version", vulnerability.path("FixedVersion").asText(""));
                item.put("description", vulnerability.path("Description").asText(
                        vulnerability.path("Title").asText("Sin descripción disponible")));
                item.put("reference", vulnerability.path("PrimaryURL").asText(""));
                item.put("recommendation", vulnerability.path("FixedVersion").asText("").isBlank()
                        ? "Sin versión corregida publicada; revisar mitigación y exposición."
                        : "Actualizar a " + vulnerability.path("FixedVersion").asText());
                items.add(item);
                counts.put(severity, counts.get(severity) + 1);
            }
        }
    }

    private double trivyScore(JsonNode vulnerability) {
        double best = 0.0;
        JsonNode cvss = vulnerability.path("CVSS");
        if (cvss.isObject()) {
            var fields = cvss.fields();
            while (fields.hasNext()) {
                JsonNode source = fields.next().getValue();
                best = Math.max(best, source.path("V4Score").asDouble(0.0));
                best = Math.max(best, source.path("V3Score").asDouble(0.0));
                best = Math.max(best, source.path("V2Score").asDouble(0.0));
            }
        }
        return best;
    }

    private double score(JsonNode vulnerability) {
        for (String version : List.of("cvssv4", "cvssv3", "cvssv2")) {
            JsonNode value = vulnerability.path(version).path("baseScore");
            if (value.isNumber()) return value.asDouble();
        }
        return 0.0;
    }

    private String severity(String reported, double score) {
        String normalized = reported.toUpperCase(Locale.ROOT);
        if (List.of("CRITICAL", "HIGH", "MEDIUM").contains(normalized)) return normalized;
        if (score >= 9.0) return "CRITICAL";
        if (score >= 7.0) return "HIGH";
        if (score >= 4.0) return "MEDIUM";
        return normalized;
    }

    private int severityOrder(String severity) {
        if ("CRITICAL".equals(severity)) return 0;
        if ("HIGH".equals(severity)) return 1;
        return 2;
    }

    private String firstReference(JsonNode vulnerability) {
        JsonNode references = vulnerability.path("references");
        return references.isArray() && !references.isEmpty()
                ? references.get(0).path("url").asText("") : "";
    }

    @PreDestroy
    public void shutdown() {
        if (process != null && process.isAlive()) process.destroy();
        executor.shutdownNow();
    }
}

package cl.bci.vaultchecker.performance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class PerformanceExecutionService {

    private final SimulationValidationService validationService;
    private final Environment environment;
    private final Path simulationsRoot;
    private final Path executionsRoot;
    private final String gatlingCommand;
    private final Path gatlingProjectDir;
    private final String secretKeyName;
    private static final AtomicInteger WORKER_SEQUENCE = new AtomicInteger(0);
    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "performance-exec-" + WORKER_SEQUENCE.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, ExecutionRecord> executions = new ConcurrentHashMap<>();
    private static final Duration QUEUED_STALE_TIMEOUT = Duration.ofMinutes(5);

    public PerformanceExecutionService(
            SimulationValidationService validationService,
            Environment environment,
            @Value("${performance.simulations-dir:${PERFORMANCE_SIMULATIONS_DIR:/app/data/simulations}}") String simulationsDir,
            @Value("${performance.executions-dir:${RESULTS_DIR:/app/data/executions}}") String executionsDir,
                @Value("${performance.gatling-command:${GATLING_COMMAND:/opt/gradle/bin/gradle}}") String gatlingCommand,
            @Value("${performance.gatling-project-dir:${GATLING_PROJECT_DIR:/app/gatling-runner}}") String gatlingProjectDir,
            @Value("${VAULT_SECRET_KEY_NAME:token}") String secretKeyName
    ) throws IOException {
        this.validationService = validationService;
        this.environment = environment;
        this.simulationsRoot = Path.of(simulationsDir).toAbsolutePath().normalize();
        this.executionsRoot = Path.of(executionsDir).toAbsolutePath().normalize();
        this.gatlingCommand = gatlingCommand;
        this.gatlingProjectDir = Path.of(gatlingProjectDir).toAbsolutePath().normalize();
        this.secretKeyName = secretKeyName;
        Files.createDirectories(this.simulationsRoot);
        Files.createDirectories(this.executionsRoot);
    }

    public Map<String, Object> upload(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        byte[] bytes = file.getBytes();
        SimulationValidationService.ValidationResult validation = validationService.validate(originalFilename, bytes);

        Map<String, Object> response = validationToMap(validation);
        if (!validation.isValid()) {
            response.put("stored", false);
            return response;
        }

        Path targetDirectory = simulationsRoot.resolve(validation.getPackageName().replace('.', '/')).normalize();
        requireInside(simulationsRoot, targetDirectory);
        Files.createDirectories(targetDirectory);
        Path target = targetDirectory.resolve(validation.getFilename()).normalize();
        requireInside(simulationsRoot, target);

        Path temporary = Files.createTempFile(targetDirectory, validation.getClassName(), ".upload");
        Files.write(temporary, bytes, StandardOpenOption.TRUNCATE_EXISTING);
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }

        response.put("stored", true);
        response.put("relative_path", simulationsRoot.relativize(target).toString().replace('\\', '/'));
        response.put("fully_qualified_class", validation.getFullyQualifiedClassName());
        return response;
    }

    public Map<String, Object> validateOnly(MultipartFile file) throws IOException {
        return validationToMap(validationService.validate(file.getOriginalFilename(), file.getBytes()));
    }

    public List<Map<String, Object>> listSimulations() throws IOException {
        List<Map<String, Object>> simulations = new ArrayList<>();
        if (!Files.exists(simulationsRoot)) {
            return simulations;
        }
        try (var paths = Files.walk(simulationsRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".scala"))
                    .sorted()
                    .forEach(path -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        String relative = simulationsRoot.relativize(path).toString().replace('\\', '/');
                        item.put("filename", path.getFileName().toString());
                        item.put("relative_path", relative);
                        item.put("class_name", relative.substring(0, relative.length() - ".scala".length()).replace('/', '.'));
                        try {
                            item.put("size", Files.size(path));
                            item.put("updated_at", Files.getLastModifiedTime(path).toInstant().toString());
                        } catch (IOException ex) {
                            item.put("metadata_error", ex.getMessage());
                        }
                        simulations.add(item);
                    });
        }
        return simulations;
    }

    public synchronized Map<String, Object> start(String fullyQualifiedClassName) throws IOException {
        validateClassName(fullyQualifiedClassName);
        markStaleExecutionsAsFailed();
        ExecutionRecord activeExecution = findActiveExecution();
        if (activeExecution != null) {
            throw new IllegalStateException("Ya existe una ejecución activa (" + activeExecution.id
                    + ") para " + activeExecution.simulationClass + "; espera a que finalice.");
        }

        Path source = simulationsRoot.resolve(fullyQualifiedClassName.replace('.', '/') + ".scala").normalize();
        requireInside(simulationsRoot, source);
        if (!Files.isRegularFile(source)) {
            throw new IllegalArgumentException("La simulación no está cargada: " + fullyQualifiedClassName);
        }

        String vaultSecret = environment.getProperty(secretKeyName);

        String executionId = Instant.now().toString().replace(':', '-') + "-" + UUID.randomUUID().toString().substring(0, 8);
        Path executionDirectory = executionsRoot.resolve(executionId).normalize();
        requireInside(executionsRoot, executionDirectory);
        Files.createDirectories(executionDirectory);
        Path logFile = executionDirectory.resolve("execution.log");
        Path resultsDirectory = executionDirectory.resolve("results");
        Files.createDirectories(resultsDirectory);

        ExecutionRecord record = new ExecutionRecord(executionId, fullyQualifiedClassName, executionDirectory, logFile);
        executions.put(executionId, record);
        appendLog(record.logFile, "[CONTROL] id=" + record.id + " host=" + runtimeHostName() + " created_at=" + record.createdAt);
        appendLog(record.logFile, "[CONTROL] Ejecución en cola para " + record.simulationClass);
        try {
            executor.submit(() -> run(record, resultsDirectory, vaultSecret));
        } catch (RejectedExecutionException ex) {
            markFailed(record, -1, "No se pudo iniciar la ejecución en el pool de workers.", ex, vaultSecret);
        }
        return record.snapshot();
    }

    private ExecutionRecord findActiveExecution() {
        return executions.values().stream()
                .filter(record -> "RUNNING".equals(record.status) || "QUEUED".equals(record.status))
                .findFirst()
                .orElse(null);
    }

    private void markStaleExecutionsAsFailed() {
        Instant now = Instant.now();
        for (ExecutionRecord record : executions.values()) {
            Process process = record.process;

            // Estados terminales no deben mantener procesos vivos; limpialos si quedaron zombies.
            if (!"RUNNING".equals(record.status) && !"QUEUED".equals(record.status)) {
                if (process != null && process.isAlive()) {
                    process.destroy();
                    try {
                        if (!process.waitFor(2, TimeUnit.SECONDS) && process.isAlive()) {
                            process.destroyForcibly();
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
                if (process != null && !process.isAlive()) {
                    record.process = null;
                }
                continue;
            }

            if (process != null && process.isAlive()) {
                continue;
            }

            // Ventana normal de arranque sin proceso aún asignado.
            if (process == null) {
                if ("QUEUED".equals(record.status)) {
                    Duration queuedAge = Duration.between(record.createdAt, now);
                    if (queuedAge.compareTo(QUEUED_STALE_TIMEOUT) < 0) {
                        continue;
                    }
                }
                if ("RUNNING".equals(record.status)) {
                    Instant runningSince = record.startedAt != null ? record.startedAt : record.createdAt;
                    Duration runningAge = Duration.between(runningSince, now);
                    if (runningAge.compareTo(QUEUED_STALE_TIMEOUT) < 0) {
                        continue;
                    }
                }
            }

            if (record.finishedAt == null) {
                markFailed(record, -1, "Ejecución previa quedó sin proceso activo.", null, null);
            }
        }
    }

    public synchronized Map<String, Object> cancel(String executionId) {
        markStaleExecutionsAsFailed();
        ExecutionRecord record = requireExecution(executionId);
        if (!"RUNNING".equals(record.status) && !"QUEUED".equals(record.status)) {
            return record.snapshot();
        }

        Process process = record.process;
        if (process != null && process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(2, TimeUnit.SECONDS) && process.isAlive()) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }

        record.status = "FAILED";
        record.exitCode = -1;
        record.finishedAt = Instant.now();
        record.lastErrorType = "MANUAL_CANCEL";
        record.lastErrorSummary = "Ejecución cancelada manualmente por un operador.";
        record.lastErrorAt = Instant.now();
        record.process = null;
        appendLog(record.logFile, "[CONTROL] Ejecución cancelada manualmente.");
        return record.snapshot();
    }

    private void run(ExecutionRecord record, Path resultsDirectory, String vaultSecret) {
        record.status = "RUNNING";
        record.startedAt = Instant.now();
        appendLog(record.logFile, "[CONTROL] Iniciando " + record.simulationClass);
        appendLog(
                record.logFile,
                "[CONTROL] Vault " + secretKeyName + ": " + maskSecret(vaultSecret)
        );
        Path commandPath = Path.of(gatlingCommand);
        if (!Files.isExecutable(commandPath)) {
            markFailed(record, -1, "No existe el ejecutable Gradle en " + gatlingCommand, null, vaultSecret);
            return;
        }
        if (!Files.isDirectory(gatlingProjectDir) || !Files.isRegularFile(gatlingProjectDir.resolve("build.gradle"))) {
            markFailed(record, -1, "No existe el proyecto runner en " + gatlingProjectDir, null, vaultSecret);
            return;
        }

        List<String> command = List.of(
                gatlingCommand,
                "--no-daemon",
                "--console=plain",
                "-p", gatlingProjectDir.toString(),
                "gatlingRun",
                "--simulation=" + record.simulationClass,
                "-Dgatling.core.directory.results=" + resultsDirectory
        );

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        processBuilder.directory(gatlingProjectDir.toFile());
        processBuilder.environment().put("PERFORMANCE_SIMULATIONS_DIR", simulationsRoot.toString());

        try {
            Process process = processBuilder.start();
            record.process = process;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    appendLog(record.logFile, sanitizeLog(line, vaultSecret));
                }
            }
            int exitCode = process.waitFor();
            record.exitCode = exitCode;
            record.status = exitCode == 0 ? "SUCCEEDED" : "FAILED";
            if (exitCode == 0) {
                collectGeneratedReport(record, resultsDirectory);
            } else {
                record.lastErrorType = "GATLING_EXIT_CODE";
                record.lastErrorSummary = "Gatling/Gradle terminó con exit code " + exitCode + ". Revisa execution.log para el detalle.";
                record.lastErrorAt = Instant.now();
                appendLog(record.logFile, "[CONTROL] ERROR: ejecución finalizó con exit code " + exitCode);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            markFailed(record, -1, "Ejecución interrumpida.", ex, vaultSecret);
        } catch (Throwable ex) {
            markFailed(record, -1, "Error inesperado durante la ejecución.", ex, vaultSecret);
        } finally {
            record.finishedAt = Instant.now();
            record.process = null;
            appendLog(record.logFile, "[CONTROL] Estado final: " + record.status);
        }
    }

    private void collectGeneratedReport(ExecutionRecord record, Path resultsDirectory) {
        Path reportsRoot = gatlingProjectDir.resolve("build/reports/gatling").normalize();
        if (!Files.isDirectory(reportsRoot)) {
            appendLog(record.logFile, "[CONTROL] ADVERTENCIA: Gatling no creó el directorio de reportes.");
            return;
        }

        try (var paths = Files.walk(reportsRoot)) {
            Path indexFile = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> "index.html".equals(path.getFileName().toString()))
                    .filter(path -> {
                        try {
                            return record.startedAt == null
                                    || !Files.getLastModifiedTime(path).toInstant().isBefore(record.startedAt.minusSeconds(60));
                        } catch (IOException ex) {
                            return false;
                        }
                    })
                    .max(Comparator.comparing(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toInstant();
                        } catch (IOException ex) {
                            return Instant.EPOCH;
                        }
                    }))
                    .orElse(null);

            if (indexFile == null) {
                appendLog(record.logFile, "[CONTROL] ADVERTENCIA: no se encontró el index.html del reporte recién generado.");
                return;
            }

            Path reportSource = indexFile.getParent().normalize();
            requireInside(reportsRoot, reportSource);
            Path reportTarget = resultsDirectory.resolve("gatling-report").normalize();
            requireInside(resultsDirectory, reportTarget);
            copyDirectory(reportSource, reportTarget);
            appendLog(record.logFile, "[CONTROL] Reporte HTML agregado a results/gatling-report/index.html");
        } catch (IOException ex) {
            appendLog(record.logFile, "[CONTROL] ADVERTENCIA: no se pudo copiar el reporte: " + ex.getMessage());
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        try (var paths = Files.walk(source)) {
            for (Path current : paths.sorted().collect(Collectors.toList())) {
                Path destination = target.resolve(source.relativize(current)).normalize();
                requireInside(target, destination);
                if (Files.isDirectory(current)) {
                    Files.createDirectories(destination);
                } else if (Files.isRegularFile(current)) {
                    Files.createDirectories(destination.getParent());
                    Files.copy(current, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    public Map<String, Object> status(String executionId) {
        markStaleExecutionsAsFailed();
        return requireExecution(executionId).snapshot();
    }

    public List<Map<String, Object>> listExecutions() {
        markStaleExecutionsAsFailed();
        return executions.values().stream()
                .sorted(Comparator.comparing((ExecutionRecord record) -> record.createdAt).reversed())
                .map(ExecutionRecord::snapshot)
                .collect(Collectors.toList());
    }

    public Map<String, Object> latestExecutionLog() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("found", false);

        if (!Files.isDirectory(executionsRoot)) {
            return response;
        }

        try (var paths = Files.list(executionsRoot)) {
            Optional<Path> latestLog = paths
                    .filter(Files::isDirectory)
                    .map(dir -> dir.resolve("execution.log"))
                    .filter(Files::isRegularFile)
                    .max(Comparator.comparing(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toInstant();
                        } catch (IOException ex) {
                            return Instant.EPOCH;
                        }
                    }));

            if (latestLog.isEmpty()) {
                return response;
            }

            Path logPath = latestLog.get();
            Path executionDir = logPath.getParent();
            String executionId = executionDir == null ? "desconocido" : executionDir.getFileName().toString();
            String content = Files.readString(logPath, StandardCharsets.UTF_8);

            String simulationClass = "desconocida";
            String status = "UNKNOWN";

            for (String line : content.split("\\R")) {
                if (line.startsWith("[CONTROL] Iniciando ")) {
                    simulationClass = line.substring("[CONTROL] Iniciando ".length()).trim();
                }
                if (line.startsWith("[CONTROL] Estado final: ")) {
                    status = line.substring("[CONTROL] Estado final: ".length()).trim();
                }
            }

            response.put("found", true);
            response.put("id", executionId);
            response.put("simulation_class", simulationClass);
            response.put("status", status);
            response.put("content", content);
            response.put("process_alive", false);
            response.put("download_ready", "SUCCEEDED".equals(status) || "FAILED".equals(status));
            return response;
        } catch (IOException ex) {
            response.put("error", ex.getMessage());
            return response;
        }
    }

    private String maskSecret(String value) {
        if (value == null || value.isBlank()) {
            return "[NO DISPONIBLE]";
        }

        if (value.length() <= 2) {
            return "*".repeat(value.length());
        }

        return value.charAt(0)
                + "*".repeat(Math.min(value.length() - 2, 10))
                + value.charAt(value.length() - 1);
    }

    public Map<String, Object> runtimeInfo() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("gatling_command", gatlingCommand);
        response.put("gatling_available", Files.isExecutable(Path.of(gatlingCommand)));
        response.put("gatling_project_dir", gatlingProjectDir.toString());
        response.put("gatling_project_available", Files.isRegularFile(gatlingProjectDir.resolve("build.gradle")));
        response.put("simulations_root", simulationsRoot.toString());
        response.put("executions_root", executionsRoot.toString());
        response.put("vault_key_name", secretKeyName);
        String secret = environment.getProperty(secretKeyName);
        boolean vaultEnabled = Boolean.parseBoolean(environment.getProperty("VAULT_ENABLED", "true"));
        response.put("credential_loaded", secret != null && !secret.isBlank());
        response.put("credential_source", vaultEnabled ? "VAULT" : "LOCAL_MOCK");
        response.put("credential_required", vaultEnabled);
        return response;
    }

    public String logs(String executionId, long offset) throws IOException {
        ExecutionRecord record = requireExecution(executionId);
        if (!Files.exists(record.logFile)) {
            return "";
        }
        byte[] bytes = Files.readAllBytes(record.logFile);
        int safeOffset = (int) Math.max(0, Math.min(offset, bytes.length));
        return new String(bytes, safeOffset, bytes.length - safeOffset, StandardCharsets.UTF_8);
    }

    public long logSize(String executionId) throws IOException {
        ExecutionRecord record = requireExecution(executionId);
        return Files.exists(record.logFile) ? Files.size(record.logFile) : 0L;
    }

    public void zipResults(String executionId, OutputStream outputStream) throws IOException {
        ExecutionRecord record = requireExecution(executionId);
        if ("RUNNING".equals(record.status) || "QUEUED".equals(record.status)) {
            throw new IllegalStateException("La ejecución todavía no finaliza.");
        }
        try (ZipOutputStream zip = new ZipOutputStream(outputStream, StandardCharsets.UTF_8);
             var paths = Files.walk(record.executionDirectory)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().collect(Collectors.toList())) {
                String entryName = record.executionDirectory.relativize(path).toString().replace('\\', '/');
                zip.putNextEntry(new ZipEntry(entryName));
                Files.copy(path, zip);
                zip.closeEntry();
            }
        }
    }

    private Map<String, Object> validationToMap(SimulationValidationService.ValidationResult validation) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", validation.isValid());
        result.put("filename", validation.getFilename());
        result.put("package_name", validation.getPackageName());
        result.put("class_name", validation.getClassName());
        result.put("fully_qualified_class", validation.getFullyQualifiedClassName());
        result.put("errors", validation.getErrors());
        result.put("warnings", validation.getWarnings());
        return result;
    }

    private void validateClassName(String className) {
        if (className == null || !className.matches("bci\\.cards\\.simulation\\.[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Clase de simulación inválida.");
        }
    }

    private void requireInside(Path root, Path candidate) {
        if (!candidate.startsWith(root)) {
            throw new IllegalArgumentException("Ruta fuera del directorio permitido.");
        }
    }

    private ExecutionRecord requireExecution(String id) {
        if (id == null || !id.matches("[A-Za-z0-9._-]{1,100}")) {
            throw new IllegalArgumentException("Identificador de ejecución inválido.");
        }
        ExecutionRecord record = executions.get(id);
        if (record == null) {
            throw new IllegalArgumentException(
                    "Ejecución no encontrada: " + id
                            + ". El pod pudo reiniciarse o limpiar memoria. "
                            + "Consulta /api/performance/executions/latest para recuperar el último log persistido."
            );
        }
        return record;
    }

    private void markFailed(ExecutionRecord record, int exitCode, String summary, Throwable error, String vaultSecret) {
        record.status = "FAILED";
        record.exitCode = exitCode;
        record.finishedAt = Instant.now();
        record.lastErrorAt = Instant.now();
        record.lastErrorType = error != null ? error.getClass().getSimpleName() : "EXECUTION_ERROR";
        record.lastErrorSummary = summary;

        appendLog(record.logFile, "[CONTROL] ERROR: " + summary);
        if (error != null) {
            String message = Objects.toString(error.getMessage(), "sin mensaje");
            appendLog(record.logFile, "[CONTROL] ERROR_TYPE: " + error.getClass().getName());
            appendLog(record.logFile, "[CONTROL] ERROR_MESSAGE: " + sanitizeLog(message, vaultSecret));
            StackTraceElement[] stack = error.getStackTrace();
            int maxLines = Math.min(stack.length, 20);
            for (int i = 0; i < maxLines; i++) {
                appendLog(record.logFile, "[CONTROL] STACK[" + i + "] " + stack[i]);
            }
            if (stack.length > maxLines) {
                appendLog(record.logFile, "[CONTROL] STACK: ... " + (stack.length - maxLines) + " líneas omitidas");
            }
        }
    }

    private String runtimeHostName() {
        String host = environment.getProperty("HOSTNAME");
        return host == null || host.isBlank() ? "unknown-host" : host;
    }

    private void appendLog(Path logFile, String line) {
        try {
            Files.writeString(logFile, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // No se imprime el contenido para evitar filtraciones de datos sensibles.
        }
    }

    private String sanitizeLog(String line, String vaultSecret) {
        if (line == null) {
            return "";
        }
        return vaultSecret == null || vaultSecret.isEmpty() ? line : line.replace(vaultSecret, "[SECRET_REDACTED]");
    }

    @PreDestroy
    public void shutdown() {
        executions.values().forEach(record -> {
            Process process = record.process;
            if (process != null && process.isAlive()) {
                process.destroy();
            }
        });
        executor.shutdownNow();
    }

    private static final class ExecutionRecord {
        private final String id;
        private final String simulationClass;
        private final Path executionDirectory;
        private final Path logFile;
        private final Instant createdAt = Instant.now();
        private volatile String status = "QUEUED";
        private volatile Instant startedAt;
        private volatile Instant finishedAt;
        private volatile Integer exitCode;
        private volatile Process process;
        private volatile String lastErrorType;
        private volatile String lastErrorSummary;
        private volatile Instant lastErrorAt;

        private ExecutionRecord(String id, String simulationClass, Path executionDirectory, Path logFile) {
            this.id = id;
            this.simulationClass = simulationClass;
            this.executionDirectory = executionDirectory;
            this.logFile = logFile;
        }

        private Map<String, Object> snapshot() {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", id);
            item.put("simulation_class", simulationClass);
            item.put("status", status);
            item.put("created_at", createdAt.toString());
            item.put("started_at", startedAt == null ? null : startedAt.toString());
            item.put("finished_at", finishedAt == null ? null : finishedAt.toString());
            item.put("exit_code", exitCode);
            item.put("process_alive", process != null && process.isAlive());
            item.put("download_ready", "SUCCEEDED".equals(status) || "FAILED".equals(status));
            item.put("error_type", lastErrorType);
            item.put("error_summary", lastErrorSummary);
            item.put("error_at", lastErrorAt == null ? null : lastErrorAt.toString());
            return item;
        }
    }
}

package cl.bci.performance.api.service;

import cl.bci.performance.api.model.ExecutionInfo;
import cl.bci.performance.api.model.ExecutionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ExecutionService {
    private final StorageService storage;
    private final ObjectMapper mapper;
    private final String javaCommand;
    private final String gatlingClasspath;
    private final ExecutorService gatlingExecutor;
    private final Map<String, ExecutionInfo> executions = new ConcurrentHashMap<>();
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final List<String> runtimeWhitelist = new CopyOnWriteArrayList<>(); // Dinámico

    public ExecutionService(
            StorageService storage,
            ObjectMapper mapper,
            ExecutorService gatlingExecutor,
            @Value("${app.runner.java-command:java}") String javaCommand,
            @Value("${app.runner.classpath:/opt/gatling/classes:/opt/gatling/lib/*}") String gatlingClasspath) {
        this.storage = storage;
        this.mapper = mapper;
        this.gatlingExecutor = gatlingExecutor;
        this.javaCommand = javaCommand;
        this.gatlingClasspath = gatlingClasspath;
    }

    public String start(String configurationId) throws IOException {
        Path configDir = storage.requireInside(
                storage.configurationsRoot(),
                storage.configurationDir(configurationId)
        );

        Path config = configDir.resolve("configuration.yaml");
        if (!Files.exists(config)) {
            throw new FileNotFoundException("Configuración no encontrada: " + config.toAbsolutePath());
        }

        // Validar que el YAML es parseable y contiene simulationClass
        String simulationClass = resolveSimulationClass(config);
        
        // Validar que el CSV existe si es requerido
        // ✅ EXCEPCIÓN: Simulaciones dinámicas compiladas en runtime NO requieren users.csv
        Path users = configDir.resolve("users.csv");
        boolean isDynamicSimulation = runtimeWhitelist.contains(simulationClass);
        boolean needsUsers = simulationClass.startsWith("bci.cards.simulation") && !isDynamicSimulation;
        
        if (needsUsers && !Files.exists(users)) {
            throw new FileNotFoundException(
                    "users.csv requerido para " + simulationClass + " pero no encontrado: " + users.toAbsolutePath()
            );
        }

        String executionId = "run-" + UUID.randomUUID();
        Path executionDir = storage.executionDir(executionId);
        Files.createDirectories(executionDir.resolve("results"));
        Files.createDirectories(executionDir.resolve("diagnostics"));

        Files.copy(config, executionDir.resolve("configuration.yaml"));
        if (Files.exists(users)) {
            Files.copy(users, executionDir.resolve("users.csv"));
        }

        ExecutionInfo info = new ExecutionInfo(
                executionId, configurationId, ExecutionStatus.QUEUED,
                null, null, "En cola - Validación exitosa: simulationClass=" + simulationClass, null, null
        );
        executions.put(executionId, info);
        persist(info, executionDir);

        gatlingExecutor.submit(() -> runExecution(info, executionDir));
        return executionId;
    }

    private void runExecution(ExecutionInfo initial, Path executionDir) {
        String id = initial.executionId();
        Path log = executionDir.resolve("execution.log");
        Path config = executionDir.resolve("configuration.yaml");
        Path users = executionDir.resolve("users.csv");

        ExecutionInfo running = new ExecutionInfo(
                id, initial.configurationId(), ExecutionStatus.RUNNING,
                null, null, "Ejecutando Gatling", Instant.now(), null
        );
        update(running, executionDir);

        String simulationClass = resolveSimulationClass(config);
        Map<String, String> mockProperties = resolveMockProperties(config);

        List<String> command = new ArrayList<>(List.of(
                javaCommand,
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "-XX:+UseG1GC",
                "-XX:InitialRAMPercentage=16.0",
                "-XX:MaxRAMPercentage=50.0",
                "-Xss512k",
                "-XX:MaxMetaspaceSize=96m",
                "-XX:MaxDirectMemorySize=96m",
                "-XX:+ExitOnOutOfMemoryError",
                "-XX:+HeapDumpOnOutOfMemoryError",
                "-XX:HeapDumpPath=" + executionDir.resolve("diagnostics/heapdump.hprof"),
                "-DconfigFile=" + config,
                "-DusersFile=" + users,
                "-cp", gatlingClasspath,
                "io.gatling.app.Gatling",
                "-s", simulationClass,
                "-rf", executionDir.resolve("results").toString(),
                "-rd", id
        ));

        if (!mockProperties.isEmpty()) {
            int classpathIndex = command.indexOf("-cp");
            List<String> properties = mockProperties.entrySet().stream()
                    .map(entry -> "-Dmock." + entry.getKey() + "=" + entry.getValue())
                    .toList();
            command.addAll(classpathIndex, properties);
        }

        int exitCode = -1;
        String failureType = null;

        try (BufferedWriter writer = Files.newBufferedWriter(
                log, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

            // Log inicial con detalles de la ejecución
            writer.write("=== GATLING TEST EXECUTION START ===\n");
            writer.write("Execution ID: " + id + "\n");
            writer.write("Simulation Class: " + simulationClass + "\n");
            writer.write("Config File: " + config + "\n");
            writer.write("Users File: " + users + " (exists: " + Files.exists(users) + ")\n");
            writer.write("Java Command: " + javaCommand + "\n");
            writer.write("Classpath: " + gatlingClasspath + "\n");
            writer.write("Working Dir: " + executionDir + "\n");
            writer.write("========================================\n\n");
            writer.flush();

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            Process process = builder.start();

            try (BufferedReader reader = process.inputReader(StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    writer.write(line);
                    writer.newLine();
                    writer.flush();
                    publish(id, line);

                    if (line.contains("OutOfMemoryError")) failureType = "JVM_HEAP_ERROR";
                    else if (line.contains("UnknownHostException")) failureType = "DNS_ERROR";
                    else if (line.contains("SSLHandshakeException")) failureType = "TLS_ERROR";
                    else if (line.contains("ConnectException")) failureType = "CONNECTION_ERROR";
                    else if (line.contains("ClassNotFoundException")) failureType = "CLASS_NOT_FOUND";
                    else if (line.contains("NoSuchMethodException")) failureType = "METHOD_NOT_FOUND";
                }
            }

            exitCode = process.waitFor();
            if (exitCode == 137) failureType = "POSSIBLE_OOM_KILLED";
            if (exitCode == 2 && failureType == null) failureType = "ASSERTION_FAILURE";

            zipDirectory(executionDir.resolve("results"), executionDir.resolve("report.zip"));

            ExecutionStatus finalStatus =
                    exitCode == 0 ? ExecutionStatus.SUCCESS :
                    exitCode == 2 ? ExecutionStatus.TEST_FAILED :
                    ExecutionStatus.PLATFORM_ERROR;

            ExecutionInfo finished = new ExecutionInfo(
                    id, initial.configurationId(), finalStatus,
                    exitCode, failureType,
                    finalStatus == ExecutionStatus.SUCCESS ? "Prueba finalizada" : "Prueba finalizada con errores (exit code: " + exitCode + ", " + failureType + ")",
                    running.startedAt(), Instant.now()
            );
            update(finished, executionDir);
            completeEmitters(id);
        } catch (Exception e) {
            ExecutionInfo failed = new ExecutionInfo(
                    id, initial.configurationId(), ExecutionStatus.PLATFORM_ERROR,
                    exitCode, failureType == null ? "EXECUTION_ERROR" : failureType,
                    e.getMessage(), running.startedAt(), Instant.now()
            );
            update(failed, executionDir);
            publish(id, "ERROR: " + e.getMessage());
            completeEmitters(id);
        }
    }



    @SuppressWarnings("unchecked")
    private Map<String, String> resolveMockProperties(Path configFile) {
        try {
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            Map<String, Object> config = yamlMapper.readValue(configFile.toFile(), Map.class);

            if (!"bci.cards.simulation.PublicMockSimulation".equals(
                    Objects.toString(config.get("simulationClass"), "")
            )) {
                return Map.of();
            }

            Object rawMock = config.get("mock");
            if (!(rawMock instanceof Map<?, ?> mock)) {
                throw new IllegalArgumentException("Falta bloque mock en configuration.yaml");
            }

            Set<String> allowedKeys = Set.of(
                    "baseUrl", "path", "method",
                    "users", "rampSeconds", "durationSeconds"
            );

            Map<String, String> result = new LinkedHashMap<>();
            for (String key : allowedKeys) {
                Object value = mock.get(key);
                if (value != null) {
                    result.put(key, value.toString());
                }
            }

            return result;
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "No se pudo leer la configuración mock",
                    e
            );
        }
    }

    @SuppressWarnings("unchecked")
    private String resolveSimulationClass(Path configFile) {
        try {
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            Map<String, Object> config = yamlMapper.readValue(configFile.toFile(), Map.class);
            String requested = Objects.toString(
                    config.getOrDefault(
                            "simulationClass",
                            "bci.cards.simulation.ConfigurableBcimsSimulation"
                    )
            );

            // Whitelist PREDEFINIDA (siempre permitida)
            Set<String> predefinedAllowed = Set.of(
                    "bci.cards.simulation.ConfigurableBcimsSimulation",
                    "bci.cards.simulation.PublicMockSimulation",
                    "bci.cards.simulation.BciLoginSmokeSimulation",
                    "bci.cards.simulation.BcimsInformacionBasicaPeakSimulation",
                    "bci.cards.simulation.BcimsInformacionBasicaTpsSimulation"
            );

            // Validar: permitida predefinida O en whitelist runtime
            if (predefinedAllowed.contains(requested) || runtimeWhitelist.contains(requested)) {
                return requested;
            }

            // Validar que sea de un paquete Gatling válido (protección básica)
            if (!requested.contains(".simulation.")) {
                throw new IllegalArgumentException(
                        "simulationClass debe estar en paquete *.simulation.*: " + requested
                );
            }

            throw new IllegalArgumentException(
                    "simulationClass no permitida: " + requested + 
                    " (compile y agregue al runtime whitelist primero)"
            );

        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "No se pudo leer simulationClass desde configuration.yaml",
                    e
            );
        }
    }

    /**
     * Agrega clase al whitelist de runtime (usado por SimulationCompilerService)
     */
    public void addToRuntimeWhitelist(String simulationClass) {
        runtimeWhitelist.add(simulationClass);
        // TODO: Validar que la clase existe en classpath
    }

    public ExecutionInfo get(String id) {
        ExecutionInfo info = executions.get(id);
        if (info != null) return info;

        Path statusFile = storage.executionDir(id).resolve("status.json");
        if (!Files.exists(statusFile)) throw new NoSuchElementException("Ejecución no encontrada");

        try {
            return mapper.readValue(statusFile.toFile(), ExecutionInfo.class);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el estado", e);
        }
    }

    public Path log(String id) {
        return storage.executionDir(id).resolve("execution.log");
    }

    public Path report(String id) {
        return storage.executionDir(id).resolve("report.zip");
    }

    public SseEmitter stream(String id) {
        get(id);
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(id, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(id, emitter));
        emitter.onTimeout(() -> removeEmitter(id, emitter));
        emitter.onError(error -> removeEmitter(id, emitter));
        return emitter;
    }

    /**
     * Publica un mensaje de log en vivo para un execution.
     * Usado por SimulationCompilerService durante compilación.
     */
    public void publishLog(String id, String line) {
        this.publish(id, line);
    }

    private void publish(String id, String line) {
        List<SseEmitter> list = emitters.getOrDefault(id, List.of());
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("log").data(line));
            } catch (IOException e) {
                emitter.complete();
                removeEmitter(id, emitter);
            }
        }
    }

    private void completeEmitters(String id) {
        for (SseEmitter emitter : emitters.getOrDefault(id, List.of())) {
            emitter.complete();
        }
        emitters.remove(id);
    }

    private void removeEmitter(String id, SseEmitter emitter) {
        emitters.getOrDefault(id, List.of()).remove(emitter);
    }

    private void update(ExecutionInfo info, Path dir) {
        executions.put(info.executionId(), info);
        persist(info, dir);
    }

    private void persist(ExecutionInfo info, Path dir) {
        try {
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(dir.resolve("status.json").toFile(), info);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo guardar el estado", e);
        }
    }

    private void zipDirectory(Path source, Path target) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
            if (!Files.exists(source)) return;
            try (var paths = Files.walk(source)) {
                paths.filter(Files::isRegularFile).forEach(path -> {
                    try {
                        String name = source.relativize(path).toString().replace('\\', '/');
                        zip.putNextEntry(new ZipEntry(name));
                        Files.copy(path, zip);
                        zip.closeEntry();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }
        }
    }
}

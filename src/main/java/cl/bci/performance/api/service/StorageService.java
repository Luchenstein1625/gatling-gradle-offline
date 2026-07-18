package cl.bci.performance.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;

@Service
public class StorageService {
    private final Path dataRoot;

    public StorageService(@Value("${app.data-root:/app/data}") String dataRoot) throws IOException {
        this.dataRoot = Paths.get(dataRoot).toAbsolutePath().normalize();
        Files.createDirectories(configurationsRoot());
        Files.createDirectories(executionsRoot());
    }

    public Path configurationsRoot() {
        return dataRoot.resolve("configurations");
    }

    public Path executionsRoot() {
        return dataRoot.resolve("executions");
    }

    public String saveConfiguration(MultipartFile configuration, MultipartFile users) throws IOException {
        String id = "cfg-" + UUID.randomUUID();
        Path dir = configurationsRoot().resolve(id);
        Files.createDirectories(dir);

        Path configTarget = dir.resolve("configuration.yaml");
        configuration.transferTo(configTarget);

        if (users != null && !users.isEmpty()) {
            users.transferTo(dir.resolve("users.csv"));
        }

        return id;
    }

    public String saveConfigurationFromString(String configuration, MultipartFile users) throws IOException {
        String id = "cfg-" + UUID.randomUUID();
        Path dir = configurationsRoot().resolve(id);
        Files.createDirectories(dir);

        Path configTarget = dir.resolve("configuration.yaml");
        Files.write(configTarget, configuration.getBytes(StandardCharsets.UTF_8));

        if (users != null && !users.isEmpty()) {
            users.transferTo(dir.resolve("users.csv"));
        }

        return id;
    }

    public Path configurationDir(String id) {
        return configurationsRoot().resolve(id).normalize();
    }

    public Path executionDir(String id) {
        return executionsRoot().resolve(id).normalize();
    }

    public Path requireInside(Path expectedRoot, Path candidate) {
        Path normalized = candidate.toAbsolutePath().normalize();
        if (!normalized.startsWith(expectedRoot.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("Ruta fuera del directorio permitido");
        }
        return normalized;
    }
}

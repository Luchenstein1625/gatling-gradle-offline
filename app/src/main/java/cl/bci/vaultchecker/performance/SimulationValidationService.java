package cl.bci.vaultchecker.performance;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SimulationValidationService {

    private static final int MAX_SOURCE_BYTES = 1024 * 1024;
    private static final Pattern PACKAGE_PATTERN = Pattern.compile(
            "(?m)^\\s*package\\s+([A-Za-z_][\\w]*(?:\\.[A-Za-z_][\\w]*)*)\\s*$"
    );
    private static final Pattern CLASS_PATTERN = Pattern.compile(
            "(?m)^\\s*(?:final\\s+)?class\\s+([A-Za-z_][\\w]*)\\s+extends\\s+Simulation\\b"
    );
    private static final List<String> BLOCKED_PATTERNS = List.of(
            "java.lang.ProcessBuilder",
            "Runtime.getRuntime",
            "scala.sys.process",
            "java.nio.file",
            "java.io.File",
            "System.exit",
            "setAccessible(",
            "Class.forName("
    );

    public ValidationResult validate(String originalFilename, byte[] bytes) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        String filename = originalFilename == null ? "" : originalFilename.trim();
        if (!filename.matches("[A-Za-z_][A-Za-z0-9_]*\\.scala")) {
            errors.add("El nombre debe ser seguro y terminar en .scala.");
        }
        if (bytes == null || bytes.length == 0) {
            errors.add("El archivo está vacío.");
        } else if (bytes.length > MAX_SOURCE_BYTES) {
            errors.add("El archivo supera el máximo permitido de 1 MiB.");
        }

        String source = bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
        if (source.indexOf('\u0000') >= 0) {
            errors.add("El archivo no parece ser texto UTF-8 válido.");
        }

        Matcher packageMatcher = PACKAGE_PATTERN.matcher(source);
        String packageName = packageMatcher.find() ? packageMatcher.group(1) : null;
        if (packageName == null) {
            errors.add("No se encontró una declaración package válida.");
        } else if (!packageName.equals("bci.cards.simulation")) {
            errors.add("El package permitido es exactamente bci.cards.simulation.");
        }

        Matcher classMatcher = CLASS_PATTERN.matcher(source);
        String className = classMatcher.find() ? classMatcher.group(1) : null;
        if (className == null) {
            errors.add("No se encontró una clase que extienda Simulation.");
        } else if (!filename.isEmpty() && !filename.equals(className + ".scala")) {
            errors.add("El archivo debe llamarse igual que la clase: " + className + ".scala");
        }

        if (!source.contains("io.gatling.core.Predef")) {
            errors.add("Falta el import io.gatling.core.Predef._");
        }
        if (!source.contains("io.gatling.http.Predef")) {
            warnings.add("No se encontró io.gatling.http.Predef._; confirmar si la simulación usa HTTP.");
        }
        if (source.contains("println(s\"access_token:")) {
            errors.add("No se permite imprimir el access_token completo en los logs.");
        }
        if (source.contains("tokenCorto")) {
            warnings.add("La simulación imprime un token abreviado; se recomienda no registrar ninguna parte del token.");
        }
        if (source.matches("(?s).*Basic\\s+[A-Za-z0-9+/=]{24,}.*")) {
            errors.add("Se detectó un posible Basic token escrito directamente en el archivo.");
        }

        for (String blocked : BLOCKED_PATTERNS) {
            if (source.contains(blocked)) {
                errors.add("Patrón no permitido en una simulación cargada: " + blocked);
            }
        }

        if (!source.contains("setUp(")) {
            warnings.add("No se encontró setUp(; la clase podría no definir una ejecución Gatling.");
        }
        if (!source.contains("assertions(")) {
            warnings.add("La simulación no contiene assertions(; se recomienda definir criterios de aceptación.");
        }

        return new ValidationResult(errors.isEmpty(), filename, packageName, className, errors, warnings);
    }

    public static final class ValidationResult {
        private final boolean valid;
        private final String filename;
        private final String packageName;
        private final String className;
        private final List<String> errors;
        private final List<String> warnings;

        public ValidationResult(boolean valid, String filename, String packageName, String className,
                                List<String> errors, List<String> warnings) {
            this.valid = valid;
            this.filename = filename;
            this.packageName = packageName;
            this.className = className;
            this.errors = List.copyOf(errors);
            this.warnings = List.copyOf(warnings);
        }

        public boolean isValid() { return valid; }
        public String getFilename() { return filename; }
        public String getPackageName() { return packageName; }
        public String getClassName() { return className; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
        public String getFullyQualifiedClassName() {
            return packageName == null || className == null ? null : packageName + "." + className;
        }
    }
}

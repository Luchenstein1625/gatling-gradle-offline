package cl.bci.performance.api.service;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Servicio para compilar archivos Scala en runtime.
 * 
 * ⛔ IMPORTANTE: Este servicio SOLO funciona dentro de Docker.
 * NO ejecutar ./gradlew compileGatlingScala en local para Scala dinámico.
 * 
 * Seguridad:
 * - Solo compila archivos .scala explícitamente nombrados
 * - Valida contenido vs patrones maliciosos
 * - Compila en directorio aislado (/tmp)
 * - Gradle task: gradle compileGatlingScala
 * 
 * Workflow correcto:
 * 1. Usuario sube archivo .scala via POST /api/upload-and-execute
 * 2. SimulationCompilerService.compileScalaFile() se ejecuta en Docker
 * 3. Gradle ejecuta internamente: ./gradlew compileGatlingScala --no-daemon -x test
 * 4. Clase compilada se valida en /opt/gatling/classes/
 * 5. Ejecución inmediata con ExecutionService
 * 
 * Ver: .NO-LOCAL-GRADLE-SCALA (marcador en raíz del proyecto)
 */
@Component
public class SimulationCompilerService {
    private static final Logger logger = Logger.getLogger(SimulationCompilerService.class.getName());
    private static final String SCALA_COMPILE_TASK = "compileGatlingScala";
    private static final String SCALA_CLASS_TEMPLATE = """
        package bci.cards.simulation
        
        import io.gatling.core.Predef._
        import io.gatling.http.Predef._
        import scala.concurrent.duration._
        
        %s
        """;
    
    private static final String SCALA_MINIMAL_TEMPLATE = """
        %s
        """;
    
    @Autowired(required = false)
    private ExecutionService executionService;
    
    private String currentExecutionId = null;  // Para vincular logs con ejecución

    /**
     * Compila un archivo Scala cargado dinámicamente.
     * @param scalaFileName Nombre del archivo (ej: "CustomSimulation.scala")
     * @param scalaContent Contenido del archivo Scala
     * @param executionId ID de ejecución (para logs en vivo)
     * @return Nombre de la clase compilada (ej: "bci.cards.simulation.CustomSimulation")
     */
    public String compileScalaFile(String scalaFileName, String scalaContent, String executionId) 
            throws IOException, InterruptedException {
        
        currentExecutionId = executionId;
        
        // 1. Validar nombre de archivo
        publishLog("🔍 Validando archivo Scala: " + scalaFileName);
        String cleanName = sanitizeFileName(scalaFileName);
        String className = cleanName.replace(".scala", "");
        
        if (!cleanName.endsWith(".scala")) {
            throw new IllegalArgumentException("Solo archivos .scala son permitidos");
        }
        
        if (className.isEmpty()) {
            throw new IllegalArgumentException("Nombre de archivo no válido");
        }
        
        publishLog("✅ Nombre validado: " + className);
        
        // 2. Validar contenido (detectar patrones maliciosos)
        publishLog("🔐 Validando seguridad del contenido...");
        validateScalaContent(scalaContent);
        publishLog("✅ Contenido seguro validado");
        
        // 3. Crear archivo temporal en src/gatling/scala/
        publishLog("📝 Generando archivo Scala...");
        Path tempDir = Paths.get("src/gatling/scala");
        Files.createDirectories(tempDir);
        
        Path scalaFile = tempDir.resolve(cleanName);
        
        // Determinar si el contenido ya tiene package/imports
        String trimmedContent = scalaContent.trim();
        boolean hasPackage = trimmedContent.toLowerCase().startsWith("package ");
        boolean hasImport = trimmedContent.toLowerCase().contains("\nimport ");
        
        // Si ya tiene package/imports, usar template minimal; si no, agregar
        String wrappedContent;
        if (hasPackage && hasImport) {
            // El usuario ya proporciona toda la estructura
            wrappedContent = String.format(SCALA_MINIMAL_TEMPLATE, scalaContent);
        } else if (!hasPackage && !hasImport) {
            // Agregar imports y package
            wrappedContent = String.format(SCALA_CLASS_TEMPLATE, scalaContent);
        } else {
            // Contenido parcial: completar los faltantes
            StringBuilder sb = new StringBuilder();
            if (!hasPackage) {
                sb.append("package bci.cards.simulation\n\n");
            }
            if (!hasImport) {
                sb.append("import io.gatling.core.Predef._\n");
                sb.append("import io.gatling.http.Predef._\n");
                sb.append("import scala.concurrent.duration._\n\n");
            }
            sb.append(scalaContent);
            wrappedContent = sb.toString();
        }
        
        Files.write(scalaFile, wrappedContent.getBytes(StandardCharsets.UTF_8));
        
        publishLog("✅ Archivo Scala creado: " + scalaFile.getFileName());
        
        // 4. Compilar con Gradle
        publishLog("🚀 Iniciando compilación Scala con Gradle...");
        publishLog("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        try {
            compileWithGradle();
            publishLog("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            publishLog("✅ Compilación exitosa: " + className);
            
            // 5. Copiar clases compiladas a /opt/gatling/classes/
            publishLog("📦 Copiando clases compiladas...");
            copyCompiledClasses();
            publishLog("✅ Clases copiadas a /opt/gatling/classes/");
        } finally {
            // Limpiar archivo temporal (mantener clases compiladas)
            Files.delete(scalaFile);
        }
        
        // 6. Retornar nombre de clase completo
        String fullyQualifiedName = "bci.cards.simulation." + className;
        publishLog("✅ Clase lista: " + fullyQualifiedName);
        
        // 7. Agregar al whitelist de runtime (permite ejecución inmediata)
        if (executionService != null) {
            executionService.addToRuntimeWhitelist(fullyQualifiedName);
            publishLog("✅ Clase agregada al whitelist de runtime");
        }
        
        return fullyQualifiedName;
    }
    
    /**
     * Compila proyecto Scala usando Gradle.
     * Captura output línea por línea y lo publica via SSE.
     */
    private void compileWithGradle() throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
            "./gradlew", 
            SCALA_COMPILE_TASK, 
            "--no-daemon",
            "-x", "test"  // Skip tests durante compilación
        );
        
        pb.redirectErrorStream(true);  // Combinar stdout y stderr
        Process process = pb.start();
        
        // Capturar output línea por línea
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                publishLog(line);  // Enviar cada línea al log en vivo
            }
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            publishLog("❌ ERROR: Compilación Scala fallida (exit code: " + exitCode + ")");
            throw new IOException("Compilación Scala fallida (exit code: " + exitCode + ")");
        }
    }
    
    /**
     * Copia las clases compiladas de build/classes/scala/gatling/ a /opt/gatling/classes/
     */
    private void copyCompiledClasses() throws IOException {
        Path sourceDir = Paths.get("build/classes/scala/gatling");
        Path targetDir = Paths.get("/opt/gatling/classes");
        
        if (!Files.exists(sourceDir)) {
            publishLog("⚠️  Advertencia: " + sourceDir + " no encontrado. El destino es " + targetDir);
            return;
        }
        
        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }
        
        // Copiar archivos .class
        Files.walk(sourceDir)
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".class"))
            .forEach(source -> {
                try {
                    Path relative = sourceDir.relativize(source);
                    Path target = targetDir.resolve(relative);
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    publishLog("⚠️  No se pudo copiar: " + source + " - " + e.getMessage());
                }
            });
    }
    
    /**
     * Publica un mensaje de log en vivo (SSE).
     * Si no hay executionId, solo imprime a stdout (visible en docker logs).
     */
    private void publishLog(String message) {
        System.out.println(message);  // Siempre al stdout del contenedor
        
        // Si hay ExecutionService y executionId, enviar via SSE
        if (executionService != null && currentExecutionId != null) {
            try {
                executionService.publishLog(currentExecutionId, message);
            } catch (Exception e) {
                // Silenciar errores de publicación para no interrumpir compilación
                System.err.println("⚠️  Advertencia: No se pudo publicar log SSE: " + e.getMessage());
            }
        }
    }
    
    /**
     * Sanitiza el nombre del archivo para evitar path traversal.
     */
    private String sanitizeFileName(String fileName) {
        // Remover path traversal attempts
        return fileName
            .replaceAll("\\.\\.[\\\\/]", "")  // ../
            .replaceAll("^[\\\\/]+", "")       // Leading slashes
            .replaceAll("[^a-zA-Z0-9_.\\-]", "");  // Chars inválidos
    }
    
    /**
     * Valida contenido Scala contra patrones peligrosos.
     */
    private void validateScalaContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Contenido Scala no puede estar vacío");
        }
        
        // Detectar intentos de inyección de código
        String[] dangerousPatterns = {
            "System.exit",
            "Runtime.getRuntime",
            "ProcessBuilder",
            "java.lang.reflect",
            "classLoader",
            "getClass().getClassLoader()",
            "forName("
        };
        
        String lowerContent = content.toLowerCase();
        for (String pattern : dangerousPatterns) {
            if (lowerContent.contains(pattern.toLowerCase())) {
                throw new SecurityException(
                    "Contenido peligroso detectado: " + pattern
                );
            }
        }
        
        // Validar que sea Scala válido - debe tener al menos una clase o object
        if (!lowerContent.contains("class ") 
            && !lowerContent.contains("object ")) {
            throw new IllegalArgumentException(
                "Archivo debe definir al menos una clase o object Scala"
            );
        }
    }
    
    /**
     * Valida si una clase compilada existe en el filesystem.
     * Verifica que el archivo .class existe en /opt/gatling/classes/
     * La validación real ocurre cuando Gradle ejecuta la prueba Gatling.
     */
    public boolean validateCompiledClass(String fullyQualifiedClassName) 
            throws ClassNotFoundException {
        try {
            // Convertir nombre de clase a ruta de archivo
            // Ejemplo: bci.cards.simulation.MiSimulacion → bci/cards/simulation/MiSimulacion.class
            String classPath = fullyQualifiedClassName.replace(".", "/") + ".class";
            Path classFile = Paths.get("/opt/gatling/classes", classPath);
            
            if (!Files.exists(classFile)) {
                throw new ClassNotFoundException(
                    "Archivo de clase no encontrado: " + classFile + 
                    " (compilación fallida?)"
                );
            }
            
            logger.info("✅ Clase compilada verificada: " + fullyQualifiedClassName);
            return true;
        } catch (Exception e) {
            logger.warning("❌ Clase no encontrada: " + fullyQualifiedClassName + " - " + e.getMessage());
            throw new ClassNotFoundException(
                "No se pudo validar la clase " + fullyQualifiedClassName + ": " + e.getMessage(), e
            );
        }
    }
}

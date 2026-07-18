package cl.bci.performance.api.service;

import org.springframework.stereotype.Service;

import javax.net.ssl.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;

@Service
public class KubernetesLogService {
    public String ownPodLogs() throws Exception {
        String host = System.getenv("KUBERNETES_SERVICE_HOST");
        String port = System.getenv().getOrDefault("KUBERNETES_SERVICE_PORT_HTTPS", "443");
        String namespace = System.getenv("POD_NAMESPACE");
        String pod = System.getenv("POD_NAME");

        if (host == null || namespace == null || pod == null) {
            throw new IllegalStateException("No se detectó entorno Kubernetes");
        }

        Path tokenPath = Paths.get("/var/run/secrets/kubernetes.io/serviceaccount/token");
        if (!Files.exists(tokenPath)) {
            throw new IllegalStateException("Token de ServiceAccount no disponible");
        }

        String token = Files.readString(tokenPath).trim();
        URI uri = URI.create("https://" + host + ":" + port +
                "/api/v1/namespaces/" + namespace + "/pods/" + pod + "/log?tailLines=1000");

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .sslContext(defaultSslContext())
                .build();

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        HttpResponse<String> response = client.send(
                request, HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() == 403) {
            throw new SecurityException("ServiceAccount sin permiso pods/log");
        }
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("Kubernetes API respondió " + response.statusCode());
        }

        return response.body();
    }

    private SSLContext defaultSslContext() throws Exception {
        // La JVM usará el truststore de la imagen. El certificado CA del
        // ServiceAccount debe incorporarse al truststore corporativo.
        return SSLContext.getDefault();
    }
}

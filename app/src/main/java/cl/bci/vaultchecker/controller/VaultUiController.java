package cl.bci.vaultchecker.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpServletResponse;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
public class VaultUiController {

    // CHANGESET-UI-CONTROLLER-2026-07-20:
    // Subir este archivo para mostrar version/timestamp de build en dashboard.

    @Value("${spring.application.name:gatling-gen3-app}")
    private String appName;

    @Value("${spring.profiles.active:default}")
    private String profile;

    @Value("${VAULT_HOST:vault-server-service.bci-infra}")
    private String vaultHost;

    @Value("${VAULT_PORT:8200}")
    private int vaultPort;

    @Value("${VAULT_AUTHENTICATION:APPROLE}")
    private String vaultAuth;

    @Value("${VAULT_TRUST_STORE:}")
    private String trustStore;

    @Value("${spring.cloud.vault.application-name:token_pruebas}")
    private String vaultKvApplicationName;

    @Value("${spring.profiles.active:qa}")
    private String vaultKvProfiles;

    @Value("${spring.cloud.vault.kv.backend:secret}")
    private String vaultBackend;

    @Value("${VAULT_SECRET_KEY_NAME:BCI_LOGIN_BASIC_AUTH}")
    private String vaultSecretKeyName;

    @Value("${app.build.display:dev}")
    private String buildDisplayVersion;

    @Value("${app.build.timestamp:unknown}")
    private String buildTimestamp;

    private final Environment environment;

    public VaultUiController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping({"/", "/menu"})
    public String menu(Model model, HttpServletResponse response) {
        disableCache(response);
        model.addAttribute("appName", appName);
        model.addAttribute("profile", profile);
        model.addAttribute("buildDisplayVersion", buildDisplayVersion);
        return "menu";
    }

    @GetMapping("/performance")
    public String performance(Model model, HttpServletResponse response) {
        disableCache(response);
        model.addAttribute("appName", appName);
        model.addAttribute("profile", profile);
        model.addAttribute("buildDisplayVersion", buildDisplayVersion);
        return "performance";
    }

    @GetMapping("/security")
    public String security(Model model, HttpServletResponse response) {
        disableCache(response);
        model.addAttribute("appName", appName);
        model.addAttribute("profile", profile);
        model.addAttribute("buildDisplayVersion", buildDisplayVersion);
        return "security";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, HttpServletResponse response) {
        disableCache(response);
        boolean networkOk = false;
        String networkError = null;

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(vaultHost, vaultPort), 1500);
            networkOk = true;
        } catch (Exception ex) {
            networkError = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        }

        boolean certExists = trustStore != null && trustStore.startsWith("file:")
                && new File(trustStore.substring("file:".length())).exists();

        Map<String, String> probedSecrets = new LinkedHashMap<>();
        probedSecrets.put("BCI_LOGIN_URL", resolveValue("BCI_LOGIN_URL"));
        probedSecrets.put("BCI_API_BASE_URL", resolveValue("BCI_API_BASE_URL"));
        probedSecrets.put("BCI_LOGIN_CLIENT_ID", resolveValue("BCI_LOGIN_CLIENT_ID"));
        probedSecrets.put("BCI_API_TOKEN", resolveValue("BCI_API_TOKEN"));
        probedSecrets.put("VAULT_TOKEN", resolveValue("VAULT_TOKEN"));

        model.addAttribute("appName", appName);
        model.addAttribute("profile", profile);
        model.addAttribute("vaultHost", vaultHost);
        model.addAttribute("vaultPort", vaultPort);
        model.addAttribute("vaultAuth", vaultAuth);
        model.addAttribute("trustStore", trustStore);
        model.addAttribute("vaultKvApplicationName", vaultKvApplicationName);
        model.addAttribute("vaultKvProfiles", vaultKvProfiles);
        model.addAttribute("vaultBackend", vaultBackend);
        model.addAttribute("vaultSecretKeyName", vaultSecretKeyName);
        model.addAttribute("buildDisplayVersion", buildDisplayVersion);
        model.addAttribute("buildTimestamp", buildTimestamp);
        model.addAttribute("networkOk", networkOk);
        model.addAttribute("networkError", networkError);
        model.addAttribute("certExists", certExists);
        model.addAttribute("probedSecrets", probedSecrets);

        return "dashboard";
    }

    private void disableCache(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
    }

    private String resolveValue(String key) {
        String value = environment.getProperty(key);
        return value == null || value.isBlank() ? "NO INYECTADO" : "INYECTADO";
    }
}

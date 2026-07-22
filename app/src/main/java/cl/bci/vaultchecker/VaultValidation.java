package cl.bci.vaultchecker;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class VaultValidation implements CommandLineRunner {

    private final Environment environment;

    @Value("${VAULT_SECRET_KEY_NAME:token}")
    private String vaultSecretKeyName;

    public VaultValidation(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(String... args) {
        String secretValue =
                environment.getProperty(vaultSecretKeyName);

        if (secretValue == null || secretValue.isBlank()) {
            System.out.println(
                    "VAULT: clave '" + vaultSecretKeyName
                            + "' NO fue encontrada"
            );
            return;
        }

        System.out.println(
                "VAULT: clave '" + vaultSecretKeyName
                        + "' cargada correctamente"
        );

        System.out.println(
                "VAULT: valor=" + maskSecret(secretValue)
        );
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
}
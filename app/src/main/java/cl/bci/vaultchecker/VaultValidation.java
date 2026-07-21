package cl.bci.vaultchecker;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class VaultValidation implements CommandLineRunner {

    @Value("${token_pruebas:}")
    private String tokenPruebas;

    @Override
    public void run(String... args) {
        if (tokenPruebas == null || tokenPruebas.isBlank()) {
            System.out.println(
                "VAULT: token_pruebas NO fue encontrado"
            );
        } else {
            System.out.println(
                "VAULT: token_pruebas cargado correctamente"
            );
            System.out.println(
                "VAULT: longitud=" + tokenPruebas.length()
            );
        }
    }
}
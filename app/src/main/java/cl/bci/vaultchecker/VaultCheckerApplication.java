package cl.bci.vaultchecker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VaultCheckerApplication {

    public static void main(String[] args) {
        SpringApplication.run(VaultCheckerApplication.class, args);
    }
}
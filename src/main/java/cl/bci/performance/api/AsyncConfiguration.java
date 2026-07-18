package cl.bci.performance.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class AsyncConfiguration {

    @Bean(destroyMethod = "shutdown")
    ExecutorService gatlingExecutor() {
        // Una sola prueba simultánea para proteger CPU y memoria.
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "gatling-execution");
            thread.setDaemon(false);
            return thread;
        });
    }
}

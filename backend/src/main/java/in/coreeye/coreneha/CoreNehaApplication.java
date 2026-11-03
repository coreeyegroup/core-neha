package in.coreeye.coreneha;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * core-neha modular monolith. Each top-level package below this one is an
 * application module (Spring Modulith); modules talk only through their
 * public API packages. See docs/adr/0001-modular-monolith.md.
 */
@SpringBootApplication
public class CoreNehaApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreNehaApplication.class, args);
    }
}

package in.coreeye.coreneha;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** Fails the build if a module reaches into another module's internals or modules form a cycle. */
class ModularityTest {

    @Test
    void moduleBoundariesAreRespected() {
        ApplicationModules.of(CoreNehaApplication.class).verify();
    }
}

package io.kaos.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class KnowledgeDatabasePathTest {
    @Test
    void usesPropertyBeforeEnvironmentAndOwnsTheFilename() {
        Path result = KnowledgeDatabasePath.resolve("data/property", "data/environment", "home");
        assertEquals(Path.of("data/property").toAbsolutePath().normalize()
                .resolve("knowledge.db"), result);
    }

    @Test
    void defaultsUnderUserHomeAndRejectsBlankConfiguration() {
        assertEquals(Path.of("home", ".kaos", "knowledge.db").toAbsolutePath().normalize(),
                KnowledgeDatabasePath.resolve(null, null, "home"));
        assertThrows(IllegalArgumentException.class,
                () -> KnowledgeDatabasePath.resolve(" ", null, "home"));
    }
}

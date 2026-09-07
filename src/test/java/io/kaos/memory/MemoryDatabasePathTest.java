package io.kaos.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MemoryDatabasePathTest {
    @Test
    void usesPropertyBeforeEnvironmentAndOwnsTheFilename() {
        Path result = MemoryDatabasePath.resolve(
                "data/property", "data/environment", "home");

        assertEquals(Path.of("data/property").toAbsolutePath().normalize()
                .resolve(MemoryDatabasePath.DATABASE_FILENAME), result);
    }

    @Test
    void defaultsUnderUserHomeAndRejectsBlankConfiguration() {
        assertEquals(Path.of("home", ".kaos", "memory.db").toAbsolutePath().normalize(),
                MemoryDatabasePath.resolve(null, null, "home"));
        assertThrows(IllegalArgumentException.class,
                () -> MemoryDatabasePath.resolve(" ", null, "home"));
        assertThrows(MemoryStorageException.class,
                () -> MemoryDatabasePath.resolve(null, null, null));
    }
}

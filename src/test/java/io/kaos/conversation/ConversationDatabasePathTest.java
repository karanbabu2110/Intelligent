package io.kaos.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConversationDatabasePathTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void usesTheSystemPropertyBeforeTheEnvironmentDirectory() {
        Path systemDirectory = temporaryDirectory.resolve("system");
        Path environmentDirectory = temporaryDirectory.resolve("environment");

        Path result = ConversationDatabasePath.resolve(
                systemDirectory.toString(), environmentDirectory.toString(), "ignored-home");

        assertEquals(systemDirectory.toAbsolutePath().normalize()
                .resolve(ConversationDatabasePath.DATABASE_FILENAME), result);
    }

    @Test
    void usesTheEnvironmentDirectoryWhenThePropertyIsAbsent() {
        Path environmentDirectory = temporaryDirectory.resolve("environment");

        Path result = ConversationDatabasePath.resolve(
                null, environmentDirectory.toString(), "ignored-home");

        assertEquals(environmentDirectory.toAbsolutePath().normalize()
                .resolve(ConversationDatabasePath.DATABASE_FILENAME), result);
    }

    @Test
    void defaultsToTheKaosDirectoryUnderTheUserHome() {
        Path result = ConversationDatabasePath.resolve(
                null, null, temporaryDirectory.toString());

        assertEquals(temporaryDirectory.resolve(".kaos")
                .resolve(ConversationDatabasePath.DATABASE_FILENAME)
                .toAbsolutePath().normalize(), result);
    }

    @Test
    void rejectsBlankConfiguredDirectoriesAndUnavailableUserHome() {
        assertThrows(IllegalArgumentException.class,
                () -> ConversationDatabasePath.resolve(" ", null, temporaryDirectory.toString()));
        assertThrows(IllegalStateException.class,
                () -> ConversationDatabasePath.resolve(null, null, null));
    }
}

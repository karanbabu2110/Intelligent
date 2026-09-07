package io.kaos.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteAnswerDetailStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsTheExactPreferenceAcrossStoreInstances() throws Exception {
        Path database = temporaryDirectory.resolve("memory.db");

        assertEquals(AnswerDetail.DETAILED,
                new SqliteAnswerDetailStore(database)
                        .create(AnswerDetailMemory.KEY, "detailed"));

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                Statement statement = connection.createStatement();
                var row = statement.executeQuery(
                        "SELECT memory_key, memory_value FROM answer_detail_memory")) {
            assertEquals(true, row.next());
            assertEquals("answer-detail", row.getString(1));
            assertEquals("detailed", row.getString(2));
            assertEquals(false, row.next());
        }
    }

    @Test
    void rejectsCreationAfterReopenWithoutOverwriting() throws Exception {
        Path database = temporaryDirectory.resolve("duplicate.db");
        new SqliteAnswerDetailStore(database).create(AnswerDetailMemory.KEY, "concise");

        MemoryCreationException exception = assertThrows(
                MemoryCreationException.class,
                () -> new SqliteAnswerDetailStore(database)
                        .create(AnswerDetailMemory.KEY, "detailed"));

        assertEquals(MemoryCreationException.Reason.ALREADY_EXISTS, exception.reason());
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                Statement statement = connection.createStatement();
                var row = statement.executeQuery(
                        "SELECT memory_value FROM answer_detail_memory")) {
            assertEquals("concise", row.getString(1));
        }
    }

    @Test
    void rejectsUnsupportedSchemaWithoutReplacingIt() throws Exception {
        Path database = temporaryDirectory.resolve("future.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE private_future_memory (value TEXT)");
            statement.execute("PRAGMA user_version = 2");
        }

        MemoryStorageException exception = assertThrows(
                MemoryStorageException.class, () -> new SqliteAnswerDetailStore(database));

        assertEquals(MemoryStorageException.Reason.INVALID_STATE, exception.reason());
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                Statement statement = connection.createStatement();
                var row = statement.executeQuery(
                        "SELECT name FROM sqlite_schema WHERE name = 'private_future_memory'")) {
            assertEquals(true, row.next());
        }
    }

    @Test
    void validatesInputBeforeWritingAnyRow() throws Exception {
        Path database = temporaryDirectory.resolve("invalid.db");
        SqliteAnswerDetailStore store = new SqliteAnswerDetailStore(database);

        assertThrows(MemoryCreationException.class,
                () -> store.create("private-key", "concise"));

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                Statement statement = connection.createStatement();
                var row = statement.executeQuery("SELECT COUNT(*) FROM answer_detail_memory")) {
            assertEquals(0, row.getInt(1));
        }
    }
}

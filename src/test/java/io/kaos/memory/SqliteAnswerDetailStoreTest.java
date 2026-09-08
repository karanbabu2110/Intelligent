package io.kaos.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteAnswerDetailStoreTest {
    @Test
    void editsAndDeletesAcrossStoreInstances() {
        Path database = temporaryDirectory.resolve("mutate.db");
        new SqliteAnswerDetailStore(database).create(AnswerDetailMemory.KEY, "concise");

        assertEquals(AnswerDetail.DETAILED, new SqliteAnswerDetailStore(database)
                .edit(AnswerDetailMemory.KEY, "detailed"));
        assertEquals(Optional.of(AnswerDetail.DETAILED),
                new SqliteAnswerDetailStore(database).retrieve());

        new SqliteAnswerDetailStore(database).delete(AnswerDetailMemory.KEY);
        assertEquals(Optional.empty(), new SqliteAnswerDetailStore(database).retrieve());
    }

    @Test
    void rejectsAbsentMutationsWithoutCreatingState() {
        Path database = temporaryDirectory.resolve("absent-mutation.db");
        SqliteAnswerDetailStore store = new SqliteAnswerDetailStore(database);

        assertThrows(MemoryMutationException.class,
                () -> store.edit(AnswerDetailMemory.KEY, "balanced"));
        assertThrows(MemoryMutationException.class,
                () -> store.delete(AnswerDetailMemory.KEY));

        assertEquals(Optional.empty(), store.retrieve());
    }

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
        assertEquals(Optional.of(AnswerDetail.DETAILED),
                new SqliteAnswerDetailStore(database).retrieve());
    }

    @Test
    void returnsEmptyWhenThePreferenceIsAbsent() {
        assertEquals(Optional.empty(), new SqliteAnswerDetailStore(
                temporaryDirectory.resolve("empty.db")).retrieve());
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

    @Test
    void rejectsInvalidStoredValuesAndUnexpectedKeys() throws Exception {
        Path invalidValueDatabase = temporaryDirectory.resolve("invalid-value.db");
        SqliteAnswerDetailStore invalidValueStore =
                new SqliteAnswerDetailStore(invalidValueDatabase);
        insertRaw(invalidValueDatabase, "answer-detail", "private free-form value");

        MemoryStorageException invalidValue = assertThrows(
                MemoryStorageException.class, invalidValueStore::retrieve);

        assertEquals(MemoryStorageException.Reason.INVALID_STATE, invalidValue.reason());

        Path invalidKeyDatabase = temporaryDirectory.resolve("invalid-key.db");
        SqliteAnswerDetailStore invalidKeyStore = new SqliteAnswerDetailStore(invalidKeyDatabase);
        insertRaw(invalidKeyDatabase, "private-key", "concise");

        MemoryStorageException invalidKey = assertThrows(
                MemoryStorageException.class, invalidKeyStore::retrieve);

        assertEquals(MemoryStorageException.Reason.INVALID_STATE, invalidKey.reason());
    }

    @Test
    void rejectsMoreThanTheSingleOwnedMemoryRow() throws Exception {
        Path database = temporaryDirectory.resolve("extra-row.db");
        SqliteAnswerDetailStore store = new SqliteAnswerDetailStore(database);
        insertRaw(database, "answer-detail", "balanced");
        insertRaw(database, "unexpected", "concise");

        MemoryStorageException exception = assertThrows(
                MemoryStorageException.class, store::retrieve);

        assertEquals(MemoryStorageException.Reason.INVALID_STATE, exception.reason());
    }

    private static void insertRaw(Path database, String key, String value) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                var statement = connection.prepareStatement(
                        "INSERT INTO answer_detail_memory (memory_key, memory_value) VALUES (?, ?)")) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        }
    }
}

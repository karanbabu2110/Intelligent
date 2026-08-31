package io.kaos.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteConversationStoreTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void storedConversationSurvivesDatabaseReopen() throws SQLException {
        Path database = initializedDatabase("reopen.db");

        new SqliteConversationStore(database).store(41);

        SqliteConversationStore reopenedStore = new SqliteConversationStore(database);
        assertEquals(List.of(41L), reopenedStore.conversationIdentifiers());
    }

    @Test
    void returnsConversationsInCreationOrderAndBeyondForegroundLimit() throws SQLException {
        Path database = initializedDatabase("ordered.db");
        SqliteConversationStore store = new SqliteConversationStore(database);
        List<Long> expected = List.of(20L, 3L, 11L, 40L, 5L, 60L, 7L, 80L, 9L, 100L, 2L, 120L);

        expected.forEach(store::store);

        assertEquals(expected, store.conversationIdentifiers());
    }

    @Test
    void boundsTheNumberOfConversationsReturned() throws SQLException {
        Path database = initializedDatabase("bounded.db");
        insertFixtureConversations(database, SqliteConversationStore.MAX_STORED_CONVERSATIONS_PER_READ + 1);

        List<Long> identifiers = new SqliteConversationStore(database).conversationIdentifiers();

        assertEquals(SqliteConversationStore.MAX_STORED_CONVERSATIONS_PER_READ, identifiers.size());
        assertEquals(1L, identifiers.getFirst());
        assertEquals(1_000L, identifiers.getLast());
    }

    @Test
    void duplicateIdentifierFailsWithoutAddingARecord() throws SQLException {
        Path database = initializedDatabase("duplicate.db");
        SqliteConversationStore store = new SqliteConversationStore(database);
        store.store(19);

        assertThrows(ConversationStorageException.class, () -> store.store(19));

        assertEquals(List.of(19L), store.conversationIdentifiers());
    }

    @Test
    void rejectsInvalidInputBeforeDatabaseWork() {
        Path missingParentDatabase = temporaryDirectory.resolve("missing").resolve("invalid.db");

        assertThrows(NullPointerException.class, () -> new SqliteConversationStore(null));
        SqliteConversationStore store = new SqliteConversationStore(missingParentDatabase);
        assertThrows(IllegalArgumentException.class, () -> store.store(0));
        assertThrows(IllegalArgumentException.class, () -> store.store(-1));
        assertFalse(temporaryDirectory.resolve("missing").toFile().exists());
    }

    @Test
    void unavailableSchemaUsesContentFreeDiagnostic() {
        Path database = temporaryDirectory.resolve("uninitialized-private-name.db");
        SqliteConversationStore store = new SqliteConversationStore(database);

        ConversationStorageException failure =
                assertThrows(ConversationStorageException.class, () -> store.store(7));

        assertEquals("Conversation storage operation failed.", failure.getMessage());
        assertFalse(failure.getMessage().contains(database.toString()));
        assertFalse(failure.getMessage().toLowerCase().contains("sql"));
        assertFalse(failure.getMessage().toLowerCase().contains("driver"));
    }

    private Path initializedDatabase(String fileName) throws SQLException {
        Path database = temporaryDirectory.resolve(fileName);
        try (Connection connection = DriverManager.getConnection(databaseUrl(database));
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE conversations (
                        sequence INTEGER PRIMARY KEY AUTOINCREMENT,
                        identifier INTEGER NOT NULL UNIQUE
                    )
                    """);
        }
        return database;
    }

    private static void insertFixtureConversations(Path database, int count) throws SQLException {
        try (Connection connection = DriverManager.getConnection(databaseUrl(database));
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO conversations (identifier) VALUES (?)")) {
            connection.setAutoCommit(false);
            for (long identifier = 1; identifier <= count; identifier++) {
                statement.setLong(1, identifier);
                statement.addBatch();
            }
            statement.executeBatch();
            connection.commit();
        }
    }

    private static String databaseUrl(Path database) {
        return "jdbc:sqlite:" + database.toAbsolutePath().normalize();
    }
}

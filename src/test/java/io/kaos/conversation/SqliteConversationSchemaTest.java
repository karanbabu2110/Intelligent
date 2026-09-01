package io.kaos.conversation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteConversationSchemaTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void rejectsCorruptDatabaseWithoutReplacingItsBytes() throws IOException {
        Path database = temporaryDirectory.resolve("private-corrupt.db");
        byte[] original = "not a sqlite database\nprivate content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(database, original);

        ConversationStorageException failure = assertThrows(
                ConversationStorageException.class,
                () -> SqliteConversationSchema.initialize(database));

        assertEquals(ConversationStorageException.Reason.CORRUPT, failure.reason());
        assertEquals("Conversation schema operation failed.", failure.getMessage());
        assertArrayEquals(original, Files.readAllBytes(database));
        assertFalse(failure.getMessage().contains(database.toString()));
        assertFalse(failure.getMessage().contains("private content"));
    }

    @Test
    void initializesVersionOneAndSupportsStoreAfterReopen() throws SQLException {
        Path database = temporaryDirectory.resolve("conversation.db");

        SqliteConversationSchema.initialize(database);
        SqliteConversationStore store = new SqliteConversationStore(database);
        store.store(7);
        store.appendMessages(7, List.of(
                new ConversationMessage(ConversationRole.USER, "hello"),
                new ConversationMessage(ConversationRole.ASSISTANT, "hi")));

        SqliteConversationSchema.initialize(database);
        assertEquals(SqliteConversationSchema.CURRENT_VERSION, schemaVersion(database));
        assertEquals(List.of(7L), new SqliteConversationStore(database).conversationIdentifiers());
        assertEquals(
                List.of(
                        new ConversationMessage(ConversationRole.USER, "hello"),
                        new ConversationMessage(ConversationRole.ASSISTANT, "hi")),
                new SqliteConversationStore(database).conversationHistory(7).messages());
        assertEquals(
                List.of("conversation_identifier", "sequence"),
                indexColumns(database, "messages_conversation_sequence_idx"));
        assertTrue(hasExpectedMessageForeignKey(database));
    }

    @Test
    void rejectsUnsupportedNewerVersionWithoutMutation() throws SQLException {
        Path database = temporaryDirectory.resolve("newer.db");
        execute(database, "PRAGMA user_version = 2");

        ConversationStorageException failure = assertThrows(
                ConversationStorageException.class,
                () -> SqliteConversationSchema.initialize(database));

        assertEquals("Conversation schema operation failed.", failure.getMessage());
        assertEquals(ConversationStorageException.Reason.INVALID_STATE, failure.reason());
        assertEquals(2, schemaVersion(database));
        assertFalse(hasObject(database, "conversations"));
        assertFalse(hasObject(database, "messages"));
    }

    @Test
    void rejectsConflictingUnversionedObjectsWithoutAddingPartialSchema() throws SQLException {
        Path database = temporaryDirectory.resolve("private-conflict.db");
        execute(database, "CREATE TABLE unrelated_private_data (value TEXT NOT NULL)");

        ConversationStorageException failure = assertThrows(
                ConversationStorageException.class,
                () -> SqliteConversationSchema.initialize(database));

        assertEquals("Conversation schema operation failed.", failure.getMessage());
        assertFalse(failure.getMessage().contains(database.toString()));
        assertEquals(0, schemaVersion(database));
        assertTrue(hasObject(database, "unrelated_private_data"));
        assertFalse(hasObject(database, "conversations"));
        assertFalse(hasObject(database, "messages"));
    }

    @Test
    void rejectsIncompleteVersionOneSchema() throws SQLException {
        Path database = temporaryDirectory.resolve("incomplete.db");
        execute(database, """
                CREATE TABLE conversations (
                    sequence INTEGER PRIMARY KEY AUTOINCREMENT,
                    identifier INTEGER NOT NULL UNIQUE
                )
                """);
        execute(database, "PRAGMA user_version = 1");

        assertThrows(
                ConversationStorageException.class,
                () -> SqliteConversationSchema.initialize(database));

        assertFalse(hasObject(database, "messages"));
        assertFalse(hasObject(database, "messages_conversation_sequence_idx"));
    }

    @Test
    void rejectsVersionOneWithoutUniqueConversationIdentifiers() throws SQLException {
        Path database = temporaryDirectory.resolve("missing-unique.db");
        execute(database, """
                CREATE TABLE conversations (
                    sequence INTEGER PRIMARY KEY AUTOINCREMENT,
                    identifier INTEGER NOT NULL
                )
                """);
        execute(database, """
                CREATE TABLE messages (
                    sequence INTEGER PRIMARY KEY AUTOINCREMENT,
                    conversation_identifier INTEGER NOT NULL,
                    role TEXT NOT NULL,
                    content TEXT NOT NULL,
                    FOREIGN KEY (conversation_identifier)
                        REFERENCES conversations(identifier)
                )
                """);
        execute(database, """
                CREATE INDEX messages_conversation_sequence_idx
                ON messages (conversation_identifier, sequence)
                """);
        execute(database, "PRAGMA user_version = 1");

        assertThrows(
                ConversationStorageException.class,
                () -> SqliteConversationSchema.initialize(database));
    }

    @Test
    void rejectsInvalidOrUnavailablePathsWithoutPrivateDiagnostics() {
        Path database = temporaryDirectory.resolve("missing").resolve("private.db");

        assertThrows(NullPointerException.class, () -> SqliteConversationSchema.initialize(null));
        ConversationStorageException failure = assertThrows(
                ConversationStorageException.class,
                () -> SqliteConversationSchema.initialize(database));

        assertEquals("Conversation schema operation failed.", failure.getMessage());
        assertFalse(failure.getMessage().contains(database.toString()));
        assertFalse(failure.getMessage().toLowerCase().contains("sql"));
        assertFalse(temporaryDirectory.resolve("missing").toFile().exists());
    }

    private static int schemaVersion(Path database) throws SQLException {
        try (Connection connection = DriverManager.getConnection(databaseUrl(database));
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("PRAGMA user_version")) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private static List<String> indexColumns(Path database, String index) throws SQLException {
        try (Connection connection = DriverManager.getConnection(databaseUrl(database));
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("PRAGMA index_info('" + index + "')")) {
            java.util.ArrayList<String> columns = new java.util.ArrayList<>();
            while (resultSet.next()) {
                columns.add(resultSet.getString("name"));
            }
            return List.copyOf(columns);
        }
    }

    private static boolean hasExpectedMessageForeignKey(Path database) throws SQLException {
        try (Connection connection = DriverManager.getConnection(databaseUrl(database));
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("PRAGMA foreign_key_list('messages')")) {
            while (resultSet.next()) {
                if ("conversations".equals(resultSet.getString("table"))
                        && "conversation_identifier".equals(resultSet.getString("from"))
                        && "identifier".equals(resultSet.getString("to"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private static boolean hasObject(Path database, String name) throws SQLException {
        try (Connection connection = DriverManager.getConnection(databaseUrl(database));
                var statement = connection.prepareStatement(
                        "SELECT 1 FROM sqlite_schema WHERE name = ?")) {
            statement.setString(1, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static void execute(Path database, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(databaseUrl(database));
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static String databaseUrl(Path database) {
        return "jdbc:sqlite:" + database.toAbsolutePath().normalize();
    }
}

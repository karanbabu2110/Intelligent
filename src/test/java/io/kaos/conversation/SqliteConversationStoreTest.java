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
    void returnsOnlyTheNewestWorkingSetInCreationOrder() throws SQLException {
        Path database = initializedDatabase("recent.db");
        SqliteConversationStore store = new SqliteConversationStore(database);
        List<Long> identifiers = List.of(20L, 3L, 11L, 40L, 5L, 60L, 7L, 80L, 9L, 100L);
        identifiers.forEach(store::store);

        assertEquals(List.of(11L, 40L, 5L, 60L, 7L, 80L, 9L, 100L),
                store.recentConversationIdentifiers(ConversationSession.MAX_CONVERSATIONS));
        assertThrows(IllegalArgumentException.class,
                () -> store.recentConversationIdentifiers(0));
        assertEquals(100L, store.greatestConversationIdentifier());
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

    @Test
    void messagesSurviveReopenWithExactContentAndRoles() throws SQLException {
        Path database = initializedDatabase("message-reopen.db");
        SqliteConversationStore store = new SqliteConversationStore(database);
        store.store(1);
        List<ConversationMessage> expected = List.of(
                new ConversationMessage(ConversationRole.USER, "Karan's question\nwith emoji: 🧠"),
                new ConversationMessage(ConversationRole.ASSISTANT, "Exact UTF-8 answer: नमस्ते"));

        store.appendMessages(1, expected);

        ConversationHistory restored =
                new SqliteConversationStore(database).conversationHistory(1);
        assertEquals(expected, restored.messages());
    }

    @Test
    void messagesAppendedInSeparateBatchesKeepInsertionOrder() throws SQLException {
        Path database = initializedDatabase("message-order.db");
        SqliteConversationStore store = new SqliteConversationStore(database);
        store.store(8);
        ConversationMessage first = new ConversationMessage(ConversationRole.USER, "first");
        ConversationMessage second = new ConversationMessage(ConversationRole.ASSISTANT, "second");
        ConversationMessage third = new ConversationMessage(ConversationRole.USER, "third");

        store.appendMessages(8, List.of(first, second));
        store.appendMessages(8, List.of(third));

        assertEquals(List.of(first, second, third), store.conversationHistory(8).messages());
    }

    @Test
    void failedMessageBatchRollsBackEveryMessage() throws SQLException {
        Path database = initializedDatabase("message-rollback.db");
        SqliteConversationStore store = new SqliteConversationStore(database);
        store.store(3);
        addRejectingMessageTrigger(database);

        assertThrows(
                ConversationStorageException.class,
                () -> store.appendMessages(3, List.of(
                        new ConversationMessage(ConversationRole.USER, "accepted-first"),
                        new ConversationMessage(ConversationRole.ASSISTANT, "reject-second"))));

        assertEquals(List.of(), store.conversationHistory(3).messages());
    }

    @Test
    void validatesMessageStorageInputBeforeDatabaseWork() {
        Path missingParentDatabase = temporaryDirectory.resolve("missing-messages").resolve("invalid.db");
        SqliteConversationStore store = new SqliteConversationStore(missingParentDatabase);
        ConversationMessage message = new ConversationMessage(ConversationRole.USER, "valid");
        List<ConversationMessage> tooManyMessages = java.util.stream.IntStream
                .rangeClosed(1, ConversationHistory.MAX_MESSAGES + 1)
                .mapToObj(index -> message)
                .toList();

        assertThrows(IllegalArgumentException.class, () -> store.appendMessages(0, List.of(message)));
        assertThrows(IllegalArgumentException.class, () -> store.appendMessages(1, null));
        assertThrows(IllegalArgumentException.class, () -> store.appendMessages(1, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> store.appendMessages(1, java.util.Arrays.asList(message, null)));
        assertThrows(
                IllegalArgumentException.class,
                () -> store.appendMessages(1, tooManyMessages));
        assertThrows(IllegalArgumentException.class, () -> store.conversationHistory(-1));
        assertFalse(temporaryDirectory.resolve("missing-messages").toFile().exists());
    }

    @Test
    void missingConversationFailsWithoutStoringMessages() throws SQLException {
        Path database = initializedDatabase("missing-conversation.db");
        SqliteConversationStore store = new SqliteConversationStore(database);

        assertThrows(
                ConversationStorageException.class,
                () -> store.appendMessages(
                        404,
                        List.of(new ConversationMessage(ConversationRole.USER, "private"))));

        assertEquals(0, messageCount(database));
    }

    @Test
    void storesMoreThanFiveHundredTwelveMessagesAcrossMoreThanEightConversations()
            throws SQLException {
        Path database = initializedDatabase("message-collection.db");
        SqliteConversationStore store = new SqliteConversationStore(database);
        List<ConversationMessage> fullHistory = java.util.stream.IntStream
                .rangeClosed(1, ConversationHistory.MAX_MESSAGES)
                .mapToObj(index -> new ConversationMessage(
                        index % 2 == 0 ? ConversationRole.ASSISTANT : ConversationRole.USER,
                        "message-" + index))
                .toList();

        for (long identifier = 1; identifier <= 9; identifier++) {
            store.store(identifier);
            store.appendMessages(identifier, fullHistory);
        }

        assertEquals(576, messageCount(database));
        assertEquals(fullHistory, store.conversationHistory(9).messages());
    }

    @Test
    void rejectsStoredHistoryBeyondTheReadBound() throws SQLException {
        Path database = initializedDatabase("message-read-bound.db");
        insertConversationAndMessages(database, 1, ConversationHistory.MAX_MESSAGES + 1, "USER");

        assertThrows(
                ConversationStorageException.class,
                () -> new SqliteConversationStore(database).conversationHistory(1));
    }

    @Test
    void rejectsInvalidStoredMessageWithoutExposingItsContent() throws SQLException {
        Path database = initializedDatabase("invalid-private-message.db");
        insertConversationAndMessages(database, 1, 1, "UNTRUSTED_PRIVATE_ROLE");

        ConversationStorageException failure = assertThrows(
                ConversationStorageException.class,
                () -> new SqliteConversationStore(database).conversationHistory(1));

        assertEquals("Conversation storage operation failed.", failure.getMessage());
        assertFalse(failure.getMessage().contains("UNTRUSTED_PRIVATE_ROLE"));
        assertFalse(failure.getMessage().contains(database.toString()));
    }

    private Path initializedDatabase(String fileName) throws SQLException {
        Path database = temporaryDirectory.resolve(fileName);
        SqliteConversationSchema.initialize(database);
        return database;
    }

    private static void addRejectingMessageTrigger(Path database) throws SQLException {
        try (Connection connection = DriverManager.getConnection(databaseUrl(database));
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TRIGGER reject_test_message
                    BEFORE INSERT ON messages
                    WHEN NEW.content = 'reject-second'
                    BEGIN
                        SELECT RAISE(ABORT, 'rejected test message');
                    END
                    """);
        }
    }

    private static int messageCount(Path database) throws SQLException {
        try (Connection connection = DriverManager.getConnection(databaseUrl(database));
                Statement statement = connection.createStatement();
                var resultSet = statement.executeQuery("SELECT COUNT(*) FROM messages")) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private static void insertConversationAndMessages(
            Path database,
            long conversationIdentifier,
            int messageCount,
            String role) throws SQLException {
        try (Connection connection = DriverManager.getConnection(databaseUrl(database));
                PreparedStatement conversationStatement = connection.prepareStatement(
                        "INSERT INTO conversations (identifier) VALUES (?)");
                PreparedStatement messageStatement = connection.prepareStatement("""
                        INSERT INTO messages (conversation_identifier, role, content)
                        VALUES (?, ?, ?)
                        """)) {
            connection.setAutoCommit(false);
            conversationStatement.setLong(1, conversationIdentifier);
            conversationStatement.executeUpdate();
            for (int index = 1; index <= messageCount; index++) {
                messageStatement.setLong(1, conversationIdentifier);
                messageStatement.setString(2, role);
                messageStatement.setString(3, "fixture-message-" + index);
                messageStatement.addBatch();
            }
            messageStatement.executeBatch();
            connection.commit();
        }
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

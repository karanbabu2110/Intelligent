package io.kaos.conversation;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Stores conversation identifiers in a caller-initialized local SQLite database.
 */
public final class SqliteConversationStore {

    static final int MAX_STORED_CONVERSATIONS_PER_READ = 1_000;

    private static final String INSERT_CONVERSATION =
            "INSERT INTO conversations (identifier) VALUES (?)";
    private static final String SELECT_CONVERSATIONS =
            "SELECT identifier FROM conversations ORDER BY sequence LIMIT ?";
    private static final String SELECT_RECENT_CONVERSATIONS = """
            SELECT identifier
            FROM (
                SELECT sequence, identifier
                FROM conversations
                ORDER BY sequence DESC
                LIMIT ?
            )
            ORDER BY sequence
            """;
    private static final String SELECT_GREATEST_CONVERSATION_IDENTIFIER =
            "SELECT COALESCE(MAX(identifier), 0) FROM conversations";
    private static final String SELECT_CONVERSATION =
            "SELECT 1 FROM conversations WHERE identifier = ?";
    private static final String COUNT_MESSAGES =
            "SELECT COUNT(*) FROM messages WHERE conversation_identifier = ?";
    private static final String INSERT_MESSAGE = """
            INSERT INTO messages (conversation_identifier, role, content)
            VALUES (?, ?, ?)
            """;
    private static final String SELECT_MESSAGES = """
            SELECT role, content
            FROM messages
            WHERE conversation_identifier = ?
            ORDER BY sequence
            LIMIT ?
            """;

    private final String databaseUrl;

    public SqliteConversationStore(Path databasePath) {
        Objects.requireNonNull(databasePath, "databasePath");
        databaseUrl = "jdbc:sqlite:" + databasePath.toAbsolutePath().normalize();
    }

    public void store(long identifier) {
        if (identifier <= 0) {
            throw new IllegalArgumentException("identifier must be positive");
        }

        try (Connection connection = DriverManager.getConnection(databaseUrl)) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(INSERT_CONVERSATION)) {
                statement.setLong(1, identifier);
                statement.executeUpdate();
                connection.commit();
            } catch (SQLException exception) {
                rollback(connection);
                throw storageFailure();
            }
        } catch (SQLException exception) {
            throw storageFailure();
        }
    }

    public List<Long> conversationIdentifiers() {
        return conversationIdentifiers(SELECT_CONVERSATIONS,
                MAX_STORED_CONVERSATIONS_PER_READ);
    }

    /** Returns the newest bounded working set in its original creation order. */
    public List<Long> recentConversationIdentifiers(int limit) {
        if (limit <= 0 || limit > MAX_STORED_CONVERSATIONS_PER_READ) {
            throw new IllegalArgumentException(
                    "limit must be between 1 and " + MAX_STORED_CONVERSATIONS_PER_READ);
        }
        return conversationIdentifiers(SELECT_RECENT_CONVERSATIONS, limit);
    }

    /** Returns zero for an empty store or the greatest durable identifier otherwise. */
    public long greatestConversationIdentifier() {
        try (Connection connection = DriverManager.getConnection(databaseUrl);
                PreparedStatement statement = connection.prepareStatement(
                        SELECT_GREATEST_CONVERSATION_IDENTIFIER);
                ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getLong(1);
        } catch (SQLException exception) {
            throw storageFailure();
        }
    }

    private List<Long> conversationIdentifiers(String sql, int limit) {
        List<Long> identifiers = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(databaseUrl);
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    identifiers.add(resultSet.getLong("identifier"));
                }
            }
        } catch (SQLException exception) {
            throw storageFailure();
        }
        return List.copyOf(identifiers);
    }

    public void appendMessages(
            long conversationIdentifier,
            List<ConversationMessage> messages) {
        validateConversationIdentifier(conversationIdentifier);
        if (messages == null) {
            throw new IllegalArgumentException("messages must not be null");
        }
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("messages must not be empty");
        }
        if (messages.size() > ConversationHistory.MAX_MESSAGES) {
            throw new IllegalArgumentException(
                    "messages must contain at most "
                            + ConversationHistory.MAX_MESSAGES + " entries");
        }
        if (messages.stream().anyMatch(message -> message == null)) {
            throw new IllegalArgumentException("messages must not contain null entries");
        }
        List<ConversationMessage> messageBatch = List.copyOf(messages);

        try (Connection connection = DriverManager.getConnection(databaseUrl)) {
            enableForeignKeys(connection);
            connection.setAutoCommit(false);
            try {
                requireConversation(connection, conversationIdentifier);
                requireMessageCapacity(connection, conversationIdentifier, messageBatch.size());
                insertMessages(connection, conversationIdentifier, messageBatch);
                connection.commit();
            } catch (SQLException exception) {
                rollback(connection);
                throw storageFailure();
            } catch (ConversationStorageException exception) {
                rollback(connection);
                throw exception;
            }
        } catch (SQLException exception) {
            throw storageFailure();
        }
    }

    public ConversationHistory conversationHistory(long conversationIdentifier) {
        validateConversationIdentifier(conversationIdentifier);
        List<ConversationMessage> messages = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(databaseUrl)) {
            requireConversation(connection, conversationIdentifier);
            try (PreparedStatement statement = connection.prepareStatement(SELECT_MESSAGES)) {
                statement.setLong(1, conversationIdentifier);
                statement.setInt(2, ConversationHistory.MAX_MESSAGES + 1);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        String role = resultSet.getString("role");
                        String content = resultSet.getString("content");
                        if (role == null || content == null) {
                            throw storageFailure();
                        }
                        messages.add(new ConversationMessage(
                                ConversationRole.valueOf(role),
                                content));
                    }
                }
            }
            if (messages.size() > ConversationHistory.MAX_MESSAGES) {
                throw storageFailure();
            }
            return new ConversationHistory(messages);
        } catch (SQLException | IllegalArgumentException exception) {
            throw storageFailure();
        }
    }

    private static void validateConversationIdentifier(long conversationIdentifier) {
        if (conversationIdentifier <= 0) {
            throw new IllegalArgumentException("conversationIdentifier must be positive");
        }
    }

    private static void enableForeignKeys(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("PRAGMA foreign_keys = ON")) {
            statement.execute();
        }
    }

    private static void requireConversation(
            Connection connection,
            long conversationIdentifier) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_CONVERSATION)) {
            statement.setLong(1, conversationIdentifier);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw storageFailure();
                }
            }
        }
    }

    private static void requireMessageCapacity(
            Connection connection,
            long conversationIdentifier,
            int additionalMessages) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(COUNT_MESSAGES)) {
            statement.setLong(1, conversationIdentifier);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                long messageCount = resultSet.getLong(1);
                if (messageCount
                        > ConversationHistory.MAX_MESSAGES - additionalMessages) {
                    throw storageFailure();
                }
            }
        }
    }

    private static void insertMessages(
            Connection connection,
            long conversationIdentifier,
            List<ConversationMessage> messages) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_MESSAGE)) {
            for (ConversationMessage message : messages) {
                statement.setLong(1, conversationIdentifier);
                statement.setString(2, message.role().name());
                statement.setString(3, message.content());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void rollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // The public failure remains content-free even if rollback also fails.
        }
    }

    private static ConversationStorageException storageFailure() {
        return new ConversationStorageException("Conversation storage operation failed.");
    }
}

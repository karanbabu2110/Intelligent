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
        List<Long> identifiers = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(databaseUrl);
                PreparedStatement statement = connection.prepareStatement(SELECT_CONVERSATIONS)) {
            statement.setInt(1, MAX_STORED_CONVERSATIONS_PER_READ);
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

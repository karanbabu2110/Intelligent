package io.kaos.conversation;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;

/** Initializes and validates the versioned local conversation schema. */
public final class SqliteConversationSchema {

    public static final int CURRENT_VERSION = 1;

    private static final String CREATE_CONVERSATIONS = """
            CREATE TABLE conversations (
                sequence INTEGER PRIMARY KEY AUTOINCREMENT,
                identifier INTEGER NOT NULL UNIQUE
            )
            """;
    private static final String CREATE_MESSAGES = """
            CREATE TABLE messages (
                sequence INTEGER PRIMARY KEY AUTOINCREMENT,
                conversation_identifier INTEGER NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                FOREIGN KEY (conversation_identifier)
                    REFERENCES conversations(identifier)
            )
            """;
    private static final String CREATE_MESSAGE_READ_INDEX = """
            CREATE INDEX messages_conversation_sequence_idx
            ON messages (conversation_identifier, sequence)
            """;

    private SqliteConversationSchema() {
    }

    public static void initialize(Path databasePath) {
        Objects.requireNonNull(databasePath, "databasePath");
        String databaseUrl = "jdbc:sqlite:" + databasePath.toAbsolutePath().normalize();

        try (Connection connection = DriverManager.getConnection(databaseUrl)) {
            connection.setAutoCommit(false);
            try {
                int version = schemaVersion(connection);
                if (version == 0) {
                    initializeVersionOne(connection);
                } else if (version != CURRENT_VERSION) {
                    throw schemaFailure();
                }
                validateVersionOne(connection);
                connection.commit();
            } catch (SQLException | ConversationStorageException exception) {
                rollback(connection);
                throw schemaFailure();
            }
        } catch (SQLException exception) {
            throw schemaFailure();
        }
    }

    private static void initializeVersionOne(Connection connection) throws SQLException {
        if (hasApplicationObjects(connection)) {
            throw schemaFailure();
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(CREATE_CONVERSATIONS);
            statement.executeUpdate(CREATE_MESSAGES);
            statement.executeUpdate(CREATE_MESSAGE_READ_INDEX);
            statement.executeUpdate("PRAGMA user_version = " + CURRENT_VERSION);
        }
    }

    private static boolean hasApplicationObjects(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("""
                        SELECT 1
                        FROM sqlite_schema
                        WHERE name NOT LIKE 'sqlite_%'
                        LIMIT 1
                        """)) {
            return resultSet.next();
        }
    }

    private static void validateVersionOne(Connection connection) throws SQLException {
        if (schemaVersion(connection) != CURRENT_VERSION) {
            throw schemaFailure();
        }
        requireColumns(connection, "conversations", List.of(
                new ColumnDefinition("sequence", "INTEGER", false, true),
                new ColumnDefinition("identifier", "INTEGER", true, false)));
        requireColumns(connection, "messages", List.of(
                new ColumnDefinition("sequence", "INTEGER", false, true),
                new ColumnDefinition("conversation_identifier", "INTEGER", true, false),
                new ColumnDefinition("role", "TEXT", true, false),
                new ColumnDefinition("content", "TEXT", true, false)));
        requireUniqueConversationIdentifier(connection);
        requireMessageForeignKey(connection);
        requireMessageReadIndex(connection);
    }

    private static void requireColumns(
            Connection connection,
            String table,
            List<ColumnDefinition> expectedColumns) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("PRAGMA table_info('" + table + "')")) {
            java.util.ArrayList<ColumnDefinition> actualColumns = new java.util.ArrayList<>();
            while (resultSet.next()) {
                actualColumns.add(new ColumnDefinition(
                        resultSet.getString("name"),
                        resultSet.getString("type"),
                        resultSet.getInt("notnull") == 1,
                        resultSet.getInt("pk") == 1));
            }
            if (!actualColumns.equals(expectedColumns)) {
                throw schemaFailure();
            }
        }
    }

    private static void requireUniqueConversationIdentifier(Connection connection)
            throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "PRAGMA index_list('conversations')")) {
            while (resultSet.next()) {
                if (resultSet.getInt("unique") == 1
                        && indexColumns(connection, resultSet.getString("name"))
                                .equals(List.of("identifier"))) {
                    return;
                }
            }
        }
        throw schemaFailure();
    }

    private static List<String> indexColumns(Connection connection, String index)
            throws SQLException {
        String quotedIndex = index.replace("'", "''");
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "PRAGMA index_info('" + quotedIndex + "')")) {
            java.util.ArrayList<String> columns = new java.util.ArrayList<>();
            while (resultSet.next()) {
                columns.add(resultSet.getString("name"));
            }
            return List.copyOf(columns);
        }
    }

    private static void requireMessageForeignKey(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("PRAGMA foreign_key_list('messages')")) {
            boolean found = false;
            while (resultSet.next()) {
                found |= "conversations".equals(resultSet.getString("table"))
                        && "conversation_identifier".equals(resultSet.getString("from"))
                        && "identifier".equals(resultSet.getString("to"));
            }
            if (!found) {
                throw schemaFailure();
            }
        }
    }

    private static void requireMessageReadIndex(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "PRAGMA index_info('messages_conversation_sequence_idx')")) {
            java.util.ArrayList<String> columns = new java.util.ArrayList<>();
            while (resultSet.next()) {
                columns.add(resultSet.getString("name"));
            }
            if (!columns.equals(List.of("conversation_identifier", "sequence"))) {
                throw schemaFailure();
            }
        }
    }

    private static int schemaVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("PRAGMA user_version")) {
            if (!resultSet.next()) {
                throw schemaFailure();
            }
            return resultSet.getInt(1);
        }
    }

    private static void rollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // The public failure remains content-free even if rollback also fails.
        }
    }

    private static ConversationStorageException schemaFailure() {
        return new ConversationStorageException("Conversation schema operation failed.");
    }

    private record ColumnDefinition(
            String name,
            String type,
            boolean notNull,
            boolean primaryKey) {
    }
}

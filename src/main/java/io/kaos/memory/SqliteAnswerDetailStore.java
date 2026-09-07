package io.kaos.memory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.sqlite.SQLiteErrorCode;

/** Versioned SQLite storage for the fixed answer-detail memory. */
public final class SqliteAnswerDetailStore implements AnswerDetailStore {
    public static final int CURRENT_VERSION = 1;
    private static final String TABLE = "answer_detail_memory";
    private final String databaseUrl;

    public SqliteAnswerDetailStore(Path databasePath) {
        Objects.requireNonNull(databasePath, "databasePath");
        Path normalized = databasePath.toAbsolutePath().normalize();
        try {
            Files.createDirectories(normalized.getParent());
        } catch (IOException | SecurityException exception) {
            throw MemoryStorageException.unavailable();
        }
        databaseUrl = "jdbc:sqlite:" + normalized;
        initialize();
    }

    @Override
    public AnswerDetail create(String key, String requestedValue) {
        AnswerDetail value = AnswerDetailMemory.validate(key, requestedValue);
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO " + TABLE + " (memory_key, memory_value) VALUES (?, ?)")) {
            statement.setString(1, AnswerDetailMemory.KEY);
            statement.setString(2, value.externalValue());
            statement.executeUpdate();
            return value;
        } catch (SQLException exception) {
            if ((exception.getErrorCode() & 0xff) == SQLiteErrorCode.SQLITE_CONSTRAINT.code) {
                throw MemoryCreationException.alreadyExists();
            }
            throw MemoryStorageException.fromSql(exception);
        }
    }

    private void initialize() {
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                int version = version(statement);
                if (version == 0) {
                    if (hasObjects(statement)) {
                        throw invalidState();
                    }
                    statement.executeUpdate("CREATE TABLE " + TABLE
                            + " (memory_key TEXT PRIMARY KEY NOT NULL,"
                            + " memory_value TEXT NOT NULL)");
                    statement.executeUpdate("PRAGMA user_version = " + CURRENT_VERSION);
                } else if (version != CURRENT_VERSION) {
                    throw invalidState();
                }
                requireSchema(statement);
                connection.commit();
            } catch (SQLException | MemoryStorageException exception) {
                rollback(connection);
                if (exception instanceof MemoryStorageException storage) {
                    throw storage;
                }
                throw MemoryStorageException.fromSql((SQLException) exception);
            }
        } catch (SQLException exception) {
            throw MemoryStorageException.fromSql(exception);
        }
    }

    private Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection(databaseUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout = 0");
        }
        return connection;
    }

    private static int version(Statement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery("PRAGMA user_version")) {
            return result.next() ? result.getInt(1) : -1;
        }
    }

    private static boolean hasObjects(Statement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery(
                "SELECT 1 FROM sqlite_schema WHERE name NOT LIKE 'sqlite_%' LIMIT 1")) {
            return result.next();
        }
    }

    private static void requireSchema(Statement statement) throws SQLException {
        List<Column> actual = new ArrayList<>();
        try (ResultSet result = statement.executeQuery("PRAGMA table_info('" + TABLE + "')")) {
            while (result.next()) {
                actual.add(new Column(
                        result.getString("name"), result.getString("type"),
                        result.getInt("notnull") == 1, result.getInt("pk") > 0));
            }
        }
        List<Column> expected = List.of(
                new Column("memory_key", "TEXT", true, true),
                new Column("memory_value", "TEXT", true, false));
        if (!actual.equals(expected)) {
            throw invalidState();
        }
    }

    private static MemoryStorageException invalidState() {
        return new MemoryStorageException(MemoryStorageException.Reason.INVALID_STATE);
    }

    private static void rollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Preserve the original content-free failure classification.
        }
    }

    private record Column(String name, String type, boolean notNull, boolean primaryKey) {
    }
}

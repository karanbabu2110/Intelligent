package io.kaos.tool.history;

import io.kaos.tool.ToolExecutionOutcome;
import io.kaos.tool.permission.ToolPermissionDecision;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Versioned bounded SQLite storage for content-free terminal tool history. */
public final class SqliteToolExecutionHistory implements ToolExecutionHistory {
    public static final int CURRENT_VERSION = 1;
    static final int MAX_STORED_RECORDS = 1_000;
    private static final String TABLE = "tool_execution_history";
    private final String databaseUrl;

    public SqliteToolExecutionHistory(Path databasePath) {
        Objects.requireNonNull(databasePath, "databasePath");
        Path normalized = databasePath.toAbsolutePath().normalize();
        try {
            Files.createDirectories(normalized.getParent());
        } catch (IOException | SecurityException exception) {
            throw ToolHistoryStorageException.unavailable();
        }
        databaseUrl = "jdbc:sqlite:" + normalized;
        initialize();
    }

    @Override
    public void record(ToolExecutionRecord record) {
        Objects.requireNonNull(record, "record");
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO " + TABLE
                    + " (operation_id, tool_name, decision, outcome, started_at, completed_at)"
                    + " VALUES (?, ?, ?, ?, ?, ?)");
                    PreparedStatement trim = connection.prepareStatement("DELETE FROM " + TABLE
                            + " WHERE sequence NOT IN (SELECT sequence FROM " + TABLE
                            + " ORDER BY sequence DESC LIMIT ?)")) {
                insert.setString(1, record.operationId().toString());
                insert.setString(2, record.toolName());
                if (record.decision().isPresent()) insert.setString(3, record.decision().orElseThrow().name());
                else insert.setNull(3, java.sql.Types.VARCHAR);
                insert.setString(4, record.outcome().name());
                insert.setString(5, record.startedAt().toString());
                insert.setString(6, record.completedAt().toString());
                insert.executeUpdate();
                trim.setInt(1, MAX_STORED_RECORDS);
                trim.executeUpdate();
                connection.commit();
            } catch (SQLException exception) {
                rollback(connection);
                throw ToolHistoryStorageException.fromSql(exception);
            }
        } catch (SQLException exception) {
            throw ToolHistoryStorageException.fromSql(exception);
        }
    }

    @Override
    public List<ToolExecutionRecord> recent(int limit) {
        if (limit <= 0 || limit > MAX_READ_RECORDS) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_READ_RECORDS);
        }
        List<ToolExecutionRecord> records = new ArrayList<>();
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement("SELECT operation_id, tool_name,"
                        + " decision, outcome, started_at, completed_at FROM " + TABLE
                        + " ORDER BY sequence DESC LIMIT ?")) {
            statement.setInt(1, limit);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) records.add(read(rows));
            }
        } catch (SQLException exception) {
            throw ToolHistoryStorageException.fromSql(exception);
        }
        return List.copyOf(records);
    }

    private ToolExecutionRecord read(ResultSet row) throws SQLException {
        try {
            String decision = row.getString("decision");
            return new ToolExecutionRecord(row.getString("tool_name"),
                    UUID.fromString(row.getString("operation_id")),
                    decision == null ? Optional.empty()
                            : Optional.of(ToolPermissionDecision.valueOf(decision)),
                    ToolExecutionOutcome.valueOf(row.getString("outcome")),
                    Instant.parse(row.getString("started_at")),
                    Instant.parse(row.getString("completed_at")));
        } catch (IllegalArgumentException | DateTimeException | NullPointerException exception) {
            throw ToolHistoryStorageException.invalidState();
        }
    }

    private void initialize() {
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                int version = version(statement);
                if (version == 0) {
                    if (hasObjects(statement)) throw ToolHistoryStorageException.invalidState();
                    statement.executeUpdate("CREATE TABLE " + TABLE + " ("
                            + "sequence INTEGER PRIMARY KEY AUTOINCREMENT,"
                            + "operation_id TEXT NOT NULL UNIQUE, tool_name TEXT NOT NULL,"
                            + "decision TEXT, outcome TEXT NOT NULL,"
                            + "started_at TEXT NOT NULL, completed_at TEXT NOT NULL)");
                    statement.executeUpdate("PRAGMA user_version = " + CURRENT_VERSION);
                } else if (version != CURRENT_VERSION) {
                    throw ToolHistoryStorageException.invalidState();
                }
                requireSchema(statement);
                connection.commit();
            } catch (SQLException | ToolHistoryStorageException exception) {
                rollback(connection);
                if (exception instanceof ToolHistoryStorageException storage) throw storage;
                throw ToolHistoryStorageException.fromSql((SQLException) exception);
            }
        } catch (SQLException exception) {
            throw ToolHistoryStorageException.fromSql(exception);
        }
    }

    private Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection(databaseUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout = 0");
            return connection;
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }
    }

    private static int version(Statement statement) throws SQLException {
        try (ResultSet row = statement.executeQuery("PRAGMA user_version")) {
            return row.next() ? row.getInt(1) : -1;
        }
    }

    private static boolean hasObjects(Statement statement) throws SQLException {
        try (ResultSet row = statement.executeQuery(
                "SELECT 1 FROM sqlite_schema WHERE name NOT LIKE 'sqlite_%' LIMIT 1")) {
            return row.next();
        }
    }

    private static void requireSchema(Statement statement) throws SQLException {
        List<String> actual = new ArrayList<>();
        try (ResultSet rows = statement.executeQuery("PRAGMA table_info('" + TABLE + "')")) {
            while (rows.next()) {
                actual.add(rows.getString("name") + ":" + rows.getString("type") + ":"
                        + rows.getInt("notnull") + ":" + rows.getInt("pk"));
            }
        }
        List<String> expected = List.of("sequence:INTEGER:0:1", "operation_id:TEXT:1:0",
                "tool_name:TEXT:1:0", "decision:TEXT:0:0", "outcome:TEXT:1:0",
                "started_at:TEXT:1:0", "completed_at:TEXT:1:0");
        if (!actual.equals(expected)) throw ToolHistoryStorageException.invalidState();
    }

    private static void rollback(Connection connection) {
        try { connection.rollback(); } catch (SQLException ignored) { }
    }
}

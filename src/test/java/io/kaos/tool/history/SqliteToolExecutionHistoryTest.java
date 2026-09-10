package io.kaos.tool.history;

import static org.junit.jupiter.api.Assertions.*;

import io.kaos.tool.ToolExecutionOutcome;
import io.kaos.tool.permission.ToolPermissionDecision;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteErrorCode;

class SqliteToolExecutionHistoryTest {
    @TempDir Path directory;

    @Test void persistsContentFreeTerminalRecordsNewestFirst() {
        Path database = directory.resolve("nested/tool-history.db");
        var first = record("read_local_file", ToolPermissionDecision.DENIED,
                ToolExecutionOutcome.DENIED, Instant.parse("2026-09-10T00:00:00Z"));
        var second = record("web_search", ToolPermissionDecision.APPROVED,
                ToolExecutionOutcome.SUCCEEDED, Instant.parse("2026-09-10T00:01:00Z"));

        var store = new SqliteToolExecutionHistory(database);
        store.record(first);
        store.record(second);

        assertEquals(java.util.List.of(second, first),
                new SqliteToolExecutionHistory(database).recent(20));
        assertFalse(store.recent(20).toString().contains("query"));
        assertThrows(UnsupportedOperationException.class, () -> store.recent(20).clear());
    }

    @Test void rejectsNonTerminalSnapshotsAndDuplicateOperationIds() {
        Instant now = Instant.parse("2026-09-10T00:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> new ToolExecutionRecord(
                "http_get", UUID.randomUUID(), Optional.of(ToolPermissionDecision.APPROVED),
                ToolExecutionOutcome.EXECUTING, now, now));
        var store = new SqliteToolExecutionHistory(directory.resolve("history.db"));
        var record = record("http_get", ToolPermissionDecision.APPROVED,
                ToolExecutionOutcome.FAILED, now);
        store.record(record);
        var exception = assertThrows(ToolHistoryStorageException.class, () -> store.record(record));
        assertEquals(ToolHistoryStorageException.Reason.INVALID_STATE, exception.reason());
        assertEquals(1, store.recent(20).size());
    }

    @Test void validatesReadBoundsAndDatabaseSchema() throws Exception {
        Path database = directory.resolve("history.db");
        var store = new SqliteToolExecutionHistory(database);
        assertTrue(store.recent(20).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> store.recent(0));
        assertThrows(IllegalArgumentException.class,
                () -> store.recent(ToolExecutionHistory.MAX_READ_RECORDS + 1));
        assertEquals(SqliteToolExecutionHistory.CURRENT_VERSION,
                pragmaVersion(database));
    }

    @Test void rejectsUnknownSchemaWithoutChangingIt() throws Exception {
        Path database = directory.resolve("unknown.db");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE private_data (secret TEXT)");
        }
        var exception = assertThrows(ToolHistoryStorageException.class,
                () -> new SqliteToolExecutionHistory(database));
        assertEquals(ToolHistoryStorageException.Reason.INVALID_STATE, exception.reason());
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                var statement = connection.createStatement();
                var row = statement.executeQuery("SELECT name FROM sqlite_schema WHERE name='private_data'")) {
            assertTrue(row.next());
        }
    }

    @Test void rejectsInvalidStoredValuesWithoutDisclosingThem() throws Exception {
        Path database = directory.resolve("invalid-row.db");
        new SqliteToolExecutionHistory(database);
        String privateValue = "private/path.txt";
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                var statement = connection.prepareStatement("INSERT INTO tool_execution_history"
                        + " (operation_id, tool_name, decision, outcome, started_at, completed_at)"
                        + " VALUES (?, ?, 'APPROVED', 'SUCCEEDED', ?, ?)")) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, privateValue);
            statement.setString(3, "2026-09-10T00:00:00Z");
            statement.setString(4, "2026-09-10T00:00:01Z");
            statement.executeUpdate();
        }
        var exception = assertThrows(ToolHistoryStorageException.class,
                () -> new SqliteToolExecutionHistory(database).recent(20));
        assertEquals(ToolHistoryStorageException.Reason.INVALID_STATE, exception.reason());
        assertFalse(exception.toString().contains(privateValue));
    }

    @Test void corruptDatabaseFailsWithoutRepairOrDisclosure() throws Exception {
        Path database = directory.resolve("corrupt.db");
        byte[] original = "private invalid sqlite bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(database, original);
        var exception = assertThrows(ToolHistoryStorageException.class,
                () -> new SqliteToolExecutionHistory(database));
        assertEquals(ToolHistoryStorageException.Reason.CORRUPT, exception.reason());
        assertArrayEquals(original, Files.readAllBytes(database));
        assertFalse(exception.toString().contains("private"));
    }

    @Test void classifiesStorageFailuresWithoutSqlDetails() {
        assertStorageReason(ToolHistoryStorageException.Reason.LOCKED,
                SQLiteErrorCode.SQLITE_BUSY);
        assertStorageReason(ToolHistoryStorageException.Reason.CORRUPT,
                SQLiteErrorCode.SQLITE_NOTADB);
        assertStorageReason(ToolHistoryStorageException.Reason.READ_ONLY,
                SQLiteErrorCode.SQLITE_READONLY);
        assertStorageReason(ToolHistoryStorageException.Reason.CAPACITY,
                SQLiteErrorCode.SQLITE_FULL);
        assertStorageReason(ToolHistoryStorageException.Reason.UNAVAILABLE,
                SQLiteErrorCode.SQLITE_CANTOPEN);
        assertStorageReason(ToolHistoryStorageException.Reason.INVALID_STATE,
                SQLiteErrorCode.SQLITE_CONSTRAINT);
    }

    @Test void databasePathUsesExplicitPrecedenceAndSafeDefault() {
        Path property = directory.resolve("property");
        Path environment = directory.resolve("environment");
        assertEquals(property.resolve(ToolHistoryDatabasePath.DATABASE_FILENAME).toAbsolutePath(),
                ToolHistoryDatabasePath.resolve(property.toString(), environment.toString(), "ignored"));
        assertEquals(environment.resolve(ToolHistoryDatabasePath.DATABASE_FILENAME).toAbsolutePath(),
                ToolHistoryDatabasePath.resolve(null, environment.toString(), "ignored"));
        assertEquals(directory.resolve(".kaos").resolve(ToolHistoryDatabasePath.DATABASE_FILENAME)
                        .toAbsolutePath(),
                ToolHistoryDatabasePath.resolve(null, null, directory.toString()));
        assertThrows(IllegalArgumentException.class,
                () -> ToolHistoryDatabasePath.resolve(" ", null, directory.toString()));
        assertThrows(ToolHistoryStorageException.class,
                () -> ToolHistoryDatabasePath.resolve(null, null, null));
    }

    @Test void trimsOldestRecordsToTheFixedRetentionBound() throws Exception {
        Path database = directory.resolve("bounded.db");
        var store = new SqliteToolExecutionHistory(database);
        Instant start = Instant.parse("2026-09-10T00:00:00Z");
        insertHistoryRows(database, SqliteToolExecutionHistory.MAX_STORED_RECORDS, start);
        store.record(record("web_search", ToolPermissionDecision.DENIED,
                ToolExecutionOutcome.DENIED,
                start.plusSeconds(SqliteToolExecutionHistory.MAX_STORED_RECORDS)));
        assertEquals(SqliteToolExecutionHistory.MAX_STORED_RECORDS, rowCount(database));
        assertEquals(start.plusSeconds(SqliteToolExecutionHistory.MAX_STORED_RECORDS + 1L),
                store.recent(1).getFirst().completedAt());
    }

    private static ToolExecutionRecord record(String tool, ToolPermissionDecision decision,
            ToolExecutionOutcome outcome, Instant started) {
        return new ToolExecutionRecord(tool, UUID.randomUUID(), Optional.of(decision), outcome,
                started, started.plusSeconds(1));
    }

    private static int pragmaVersion(Path database) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                var statement = connection.createStatement();
                var row = statement.executeQuery("PRAGMA user_version")) {
            return row.getInt(1);
        }
    }

    private static void assertStorageReason(ToolHistoryStorageException.Reason expected,
            SQLiteErrorCode code) {
        String privateSql = "private SQL and path";
        var exception = ToolHistoryStorageException.fromSql(
                new java.sql.SQLException(privateSql, "", code.code));
        assertEquals(expected, exception.reason());
        assertFalse(exception.toString().contains(privateSql));
    }

    private static int rowCount(Path database) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                var statement = connection.createStatement();
                var row = statement.executeQuery("SELECT COUNT(*) FROM tool_execution_history")) {
            return row.getInt(1);
        }
    }

    private static void insertHistoryRows(Path database, int count, Instant start) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                var statement = connection.prepareStatement("INSERT INTO tool_execution_history"
                        + " (operation_id, tool_name, decision, outcome, started_at, completed_at)"
                        + " VALUES (?, 'web_search', 'DENIED', 'DENIED', ?, ?)")) {
            connection.setAutoCommit(false);
            for (int index = 0; index < count; index++) {
                statement.setString(1, UUID.randomUUID().toString());
                statement.setString(2, start.plusSeconds(index).toString());
                statement.setString(3, start.plusSeconds(index + 1L).toString());
                statement.addBatch();
            }
            statement.executeBatch();
            connection.commit();
        }
    }
}

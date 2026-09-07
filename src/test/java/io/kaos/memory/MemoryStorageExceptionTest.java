package io.kaos.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteErrorCode;

class MemoryStorageExceptionTest {
    @Test
    void classifiesRepresentativeSqliteFailuresWithoutRetainingDriverDetail() {
        assertReason(SQLiteErrorCode.SQLITE_BUSY, MemoryStorageException.Reason.LOCKED);
        assertReason(SQLiteErrorCode.SQLITE_CORRUPT, MemoryStorageException.Reason.CORRUPT);
        assertReason(SQLiteErrorCode.SQLITE_READONLY, MemoryStorageException.Reason.READ_ONLY);
        assertReason(SQLiteErrorCode.SQLITE_FULL, MemoryStorageException.Reason.CAPACITY);
        assertReason(SQLiteErrorCode.SQLITE_CANTOPEN, MemoryStorageException.Reason.UNAVAILABLE);
        assertReason(SQLiteErrorCode.SQLITE_SCHEMA, MemoryStorageException.Reason.INVALID_STATE);

        MemoryStorageException unknown = MemoryStorageException.fromSql(
                new SQLException("private driver detail", "state", 999));
        assertEquals(MemoryStorageException.Reason.UNKNOWN, unknown.reason());
        assertEquals("Memory storage operation failed.", unknown.getMessage());
    }

    private static void assertReason(
            SQLiteErrorCode code, MemoryStorageException.Reason expected) {
        MemoryStorageException exception = MemoryStorageException.fromSql(
                new SQLException("private driver detail", "state", code.code));

        assertEquals(expected, exception.reason());
        assertEquals("Memory storage operation failed.", exception.getMessage());
    }
}

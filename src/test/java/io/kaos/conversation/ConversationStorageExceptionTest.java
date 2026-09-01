package io.kaos.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

class ConversationStorageExceptionTest {
    @Test
    void classifiesPrimaryAndExtendedSqliteCodesWithoutExposingDriverDetail() {
        Map<ConversationStorageException.Reason, List<SQLiteErrorCode>> cases =
                new LinkedHashMap<>();
        cases.put(ConversationStorageException.Reason.LOCKED,
                List.of(SQLiteErrorCode.SQLITE_BUSY, SQLiteErrorCode.SQLITE_BUSY_TIMEOUT,
                        SQLiteErrorCode.SQLITE_LOCKED_SHAREDCACHE));
        cases.put(ConversationStorageException.Reason.CORRUPT,
                List.of(SQLiteErrorCode.SQLITE_CORRUPT,
                        SQLiteErrorCode.SQLITE_CORRUPT_INDEX,
                        SQLiteErrorCode.SQLITE_NOTADB));
        cases.put(ConversationStorageException.Reason.READ_ONLY,
                List.of(SQLiteErrorCode.SQLITE_READONLY,
                        SQLiteErrorCode.SQLITE_READONLY_DIRECTORY,
                        SQLiteErrorCode.SQLITE_PERM));
        cases.put(ConversationStorageException.Reason.CAPACITY,
                List.of(SQLiteErrorCode.SQLITE_FULL, SQLiteErrorCode.SQLITE_NOMEM,
                        SQLiteErrorCode.SQLITE_TOOBIG));
        cases.put(ConversationStorageException.Reason.UNAVAILABLE,
                List.of(SQLiteErrorCode.SQLITE_CANTOPEN,
                        SQLiteErrorCode.SQLITE_IOERR_WRITE,
                        SQLiteErrorCode.SQLITE_NOLFS));
        cases.put(ConversationStorageException.Reason.INVALID_STATE,
                List.of(SQLiteErrorCode.SQLITE_CONSTRAINT_TRIGGER,
                        SQLiteErrorCode.SQLITE_SCHEMA,
                        SQLiteErrorCode.SQLITE_MISMATCH));

        cases.forEach((reason, errorCodes) -> errorCodes.forEach(errorCode -> {
            String privateDetail = "private driver detail " + errorCode;
            ConversationStorageException failure = ConversationStorageException.fromSql(
                    new SQLiteException(privateDetail, errorCode),
                    "Conversation storage operation failed.");

            assertEquals(reason, failure.reason());
            assertEquals("Conversation storage operation failed.", failure.getMessage());
            assertFalse(failure.getMessage().contains(privateDetail));
        }));
    }

    @Test
    void keepsUnknownSqlFailuresContentFree() {
        ConversationStorageException failure = ConversationStorageException.fromSql(
                new SQLException("private unknown detail", "private-state", 0),
                "Conversation storage operation failed.");

        assertEquals(ConversationStorageException.Reason.UNKNOWN, failure.reason());
        assertEquals("Conversation storage operation failed.", failure.getMessage());
        assertFalse(failure.getMessage().contains("private"));
    }
}

package io.kaos.tool.history;

import java.sql.SQLException;
import java.util.Objects;
import org.sqlite.SQLiteErrorCode;

/** Content-free local tool-history storage failure. */
public final class ToolHistoryStorageException extends RuntimeException {
    public enum Reason { LOCKED, CORRUPT, READ_ONLY, CAPACITY, UNAVAILABLE, INVALID_STATE, UNKNOWN }

    private final Reason reason;

    ToolHistoryStorageException(Reason reason) {
        super("Tool execution history storage failed.");
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public Reason reason() { return reason; }

    static ToolHistoryStorageException fromSql(SQLException exception) {
        Objects.requireNonNull(exception, "exception");
        for (SQLException current = exception; current != null; current = current.getNextException()) {
            int code = current.getErrorCode() & 0xff;
            if (code == SQLiteErrorCode.SQLITE_BUSY.code || code == SQLiteErrorCode.SQLITE_LOCKED.code) return new ToolHistoryStorageException(Reason.LOCKED);
            if (code == SQLiteErrorCode.SQLITE_CORRUPT.code || code == SQLiteErrorCode.SQLITE_NOTADB.code || code == SQLiteErrorCode.SQLITE_FORMAT.code) return new ToolHistoryStorageException(Reason.CORRUPT);
            if (code == SQLiteErrorCode.SQLITE_READONLY.code || code == SQLiteErrorCode.SQLITE_PERM.code || code == SQLiteErrorCode.SQLITE_AUTH.code) return new ToolHistoryStorageException(Reason.READ_ONLY);
            if (code == SQLiteErrorCode.SQLITE_FULL.code || code == SQLiteErrorCode.SQLITE_NOMEM.code || code == SQLiteErrorCode.SQLITE_TOOBIG.code) return new ToolHistoryStorageException(Reason.CAPACITY);
            if (code == SQLiteErrorCode.SQLITE_CANTOPEN.code || code == SQLiteErrorCode.SQLITE_IOERR.code || code == SQLiteErrorCode.SQLITE_NOLFS.code || code == SQLiteErrorCode.SQLITE_NOTFOUND.code) return new ToolHistoryStorageException(Reason.UNAVAILABLE);
            if (code == SQLiteErrorCode.SQLITE_CONSTRAINT.code || code == SQLiteErrorCode.SQLITE_SCHEMA.code || code == SQLiteErrorCode.SQLITE_MISMATCH.code || code == SQLiteErrorCode.SQLITE_RANGE.code || code == SQLiteErrorCode.SQLITE_MISUSE.code) return new ToolHistoryStorageException(Reason.INVALID_STATE);
        }
        return new ToolHistoryStorageException(Reason.UNKNOWN);
    }

    public static ToolHistoryStorageException unavailable() {
        return new ToolHistoryStorageException(Reason.UNAVAILABLE);
    }

    static ToolHistoryStorageException invalidState() {
        return new ToolHistoryStorageException(Reason.INVALID_STATE);
    }
}

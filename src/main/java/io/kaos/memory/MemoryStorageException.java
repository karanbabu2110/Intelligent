package io.kaos.memory;

import java.sql.SQLException;
import java.util.Objects;
import org.sqlite.SQLiteErrorCode;

/** Content-free local memory storage failure. */
public final class MemoryStorageException extends RuntimeException {
    public enum Reason {
        LOCKED,
        CORRUPT,
        READ_ONLY,
        CAPACITY,
        UNAVAILABLE,
        INVALID_STATE,
        UNKNOWN
    }

    private final Reason reason;

    MemoryStorageException(Reason reason) {
        super("Memory storage operation failed.");
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public Reason reason() {
        return reason;
    }

    public static MemoryStorageException unavailable() {
        return new MemoryStorageException(Reason.UNAVAILABLE);
    }

    static MemoryStorageException fromSql(SQLException exception) {
        for (SQLException current = exception; current != null;
                current = current.getNextException()) {
            int code = current.getErrorCode() & 0xff;
            if (code == SQLiteErrorCode.SQLITE_BUSY.code
                    || code == SQLiteErrorCode.SQLITE_LOCKED.code) {
                return new MemoryStorageException(Reason.LOCKED);
            }
            if (code == SQLiteErrorCode.SQLITE_CORRUPT.code
                    || code == SQLiteErrorCode.SQLITE_NOTADB.code
                    || code == SQLiteErrorCode.SQLITE_FORMAT.code) {
                return new MemoryStorageException(Reason.CORRUPT);
            }
            if (code == SQLiteErrorCode.SQLITE_READONLY.code
                    || code == SQLiteErrorCode.SQLITE_PERM.code
                    || code == SQLiteErrorCode.SQLITE_AUTH.code) {
                return new MemoryStorageException(Reason.READ_ONLY);
            }
            if (code == SQLiteErrorCode.SQLITE_FULL.code
                    || code == SQLiteErrorCode.SQLITE_NOMEM.code
                    || code == SQLiteErrorCode.SQLITE_TOOBIG.code) {
                return new MemoryStorageException(Reason.CAPACITY);
            }
            if (code == SQLiteErrorCode.SQLITE_CANTOPEN.code
                    || code == SQLiteErrorCode.SQLITE_IOERR.code
                    || code == SQLiteErrorCode.SQLITE_NOLFS.code
                    || code == SQLiteErrorCode.SQLITE_NOTFOUND.code) {
                return new MemoryStorageException(Reason.UNAVAILABLE);
            }
            if (code == SQLiteErrorCode.SQLITE_SCHEMA.code
                    || code == SQLiteErrorCode.SQLITE_MISMATCH.code
                    || code == SQLiteErrorCode.SQLITE_RANGE.code
                    || code == SQLiteErrorCode.SQLITE_MISUSE.code) {
                return new MemoryStorageException(Reason.INVALID_STATE);
            }
        }
        return new MemoryStorageException(Reason.UNKNOWN);
    }
}

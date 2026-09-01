package io.kaos.conversation;

import java.sql.SQLException;
import java.util.Objects;
import org.sqlite.SQLiteErrorCode;

/**
 * Reports a local conversation storage failure without exposing database details.
 */
public final class ConversationStorageException extends RuntimeException {
    /** Stable recovery categories independent of private SQL and filesystem details. */
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

    ConversationStorageException(Reason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public Reason reason() {
        return reason;
    }

    static ConversationStorageException fromSql(SQLException exception, String message) {
        Objects.requireNonNull(exception, "exception");
        return new ConversationStorageException(classify(exception), message);
    }

    private static Reason classify(SQLException exception) {
        for (SQLException current = exception; current != null;
                current = current.getNextException()) {
            Reason reason = classifyPrimaryCode(current.getErrorCode() & 0xff);
            if (reason != Reason.UNKNOWN) {
                return reason;
            }
        }
        return Reason.UNKNOWN;
    }

    private static Reason classifyPrimaryCode(int code) {
        if (code == SQLiteErrorCode.SQLITE_BUSY.code
                || code == SQLiteErrorCode.SQLITE_LOCKED.code) {
            return Reason.LOCKED;
        }
        if (code == SQLiteErrorCode.SQLITE_CORRUPT.code
                || code == SQLiteErrorCode.SQLITE_NOTADB.code
                || code == SQLiteErrorCode.SQLITE_FORMAT.code) {
            return Reason.CORRUPT;
        }
        if (code == SQLiteErrorCode.SQLITE_READONLY.code
                || code == SQLiteErrorCode.SQLITE_PERM.code
                || code == SQLiteErrorCode.SQLITE_AUTH.code) {
            return Reason.READ_ONLY;
        }
        if (code == SQLiteErrorCode.SQLITE_FULL.code
                || code == SQLiteErrorCode.SQLITE_NOMEM.code
                || code == SQLiteErrorCode.SQLITE_TOOBIG.code) {
            return Reason.CAPACITY;
        }
        if (code == SQLiteErrorCode.SQLITE_CANTOPEN.code
                || code == SQLiteErrorCode.SQLITE_IOERR.code
                || code == SQLiteErrorCode.SQLITE_NOLFS.code
                || code == SQLiteErrorCode.SQLITE_NOTFOUND.code) {
            return Reason.UNAVAILABLE;
        }
        if (code == SQLiteErrorCode.SQLITE_CONSTRAINT.code
                || code == SQLiteErrorCode.SQLITE_SCHEMA.code
                || code == SQLiteErrorCode.SQLITE_MISMATCH.code
                || code == SQLiteErrorCode.SQLITE_RANGE.code
                || code == SQLiteErrorCode.SQLITE_MISUSE.code) {
            return Reason.INVALID_STATE;
        }
        return Reason.UNKNOWN;
    }
}

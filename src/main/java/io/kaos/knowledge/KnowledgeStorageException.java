package io.kaos.knowledge;

import java.sql.SQLException;
import java.util.Objects;
import org.sqlite.SQLiteErrorCode;

/** Content-free local knowledge storage failure. */
public final class KnowledgeStorageException extends RuntimeException {
    public enum Reason { LOCKED, CORRUPT, READ_ONLY, CAPACITY, UNAVAILABLE, INVALID_STATE, UNKNOWN }
    private final Reason reason;

    KnowledgeStorageException(Reason reason) {
        super("Knowledge storage operation failed.");
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public Reason reason() { return reason; }

    /** Creates a safe unavailable-path failure without retaining path details. */
    public static KnowledgeStorageException unavailable() {
        return new KnowledgeStorageException(Reason.UNAVAILABLE);
    }

    static KnowledgeStorageException fromSql(SQLException exception) {
        for (SQLException current = exception; current != null; current = current.getNextException()) {
            int code = current.getErrorCode() & 0xff;
            if (code == SQLiteErrorCode.SQLITE_BUSY.code || code == SQLiteErrorCode.SQLITE_LOCKED.code) return new KnowledgeStorageException(Reason.LOCKED);
            if (code == SQLiteErrorCode.SQLITE_CORRUPT.code || code == SQLiteErrorCode.SQLITE_NOTADB.code || code == SQLiteErrorCode.SQLITE_FORMAT.code) return new KnowledgeStorageException(Reason.CORRUPT);
            if (code == SQLiteErrorCode.SQLITE_READONLY.code || code == SQLiteErrorCode.SQLITE_PERM.code || code == SQLiteErrorCode.SQLITE_AUTH.code) return new KnowledgeStorageException(Reason.READ_ONLY);
            if (code == SQLiteErrorCode.SQLITE_FULL.code || code == SQLiteErrorCode.SQLITE_NOMEM.code || code == SQLiteErrorCode.SQLITE_TOOBIG.code) return new KnowledgeStorageException(Reason.CAPACITY);
            if (code == SQLiteErrorCode.SQLITE_CANTOPEN.code || code == SQLiteErrorCode.SQLITE_IOERR.code || code == SQLiteErrorCode.SQLITE_NOLFS.code || code == SQLiteErrorCode.SQLITE_NOTFOUND.code) return new KnowledgeStorageException(Reason.UNAVAILABLE);
            if (code == SQLiteErrorCode.SQLITE_CONSTRAINT.code || code == SQLiteErrorCode.SQLITE_SCHEMA.code || code == SQLiteErrorCode.SQLITE_MISMATCH.code || code == SQLiteErrorCode.SQLITE_RANGE.code || code == SQLiteErrorCode.SQLITE_MISUSE.code) return new KnowledgeStorageException(Reason.INVALID_STATE);
        }
        return new KnowledgeStorageException(Reason.UNKNOWN);
    }
}

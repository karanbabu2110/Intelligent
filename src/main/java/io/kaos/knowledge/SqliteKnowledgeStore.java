package io.kaos.knowledge;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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

/** Versioned SQLite storage for exact chunks and their local embedding vectors. */
public final class SqliteKnowledgeStore {
    public static final int CURRENT_VERSION = 1;
    private final String databaseUrl;

    public SqliteKnowledgeStore(Path databasePath) {
        Objects.requireNonNull(databasePath, "databasePath");
        Path normalized = databasePath.toAbsolutePath().normalize();
        try {
            Files.createDirectories(normalized.getParent());
        } catch (IOException | SecurityException exception) {
            throw new KnowledgeStorageException(KnowledgeStorageException.Reason.UNAVAILABLE);
        }
        databaseUrl = "jdbc:sqlite:" + normalized;
        initialize();
    }

    /** Atomically stores one complete ordered document snapshot and returns its identifier. */
    public long store(String embeddingModel, List<EmbeddedChunk> chunks) {
        validate(embeddingModel, chunks);
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                long identifier = insertDocument(connection, embeddingModel, chunks);
                insertChunks(connection, identifier, chunks);
                connection.commit();
                return identifier;
            } catch (SQLException | KnowledgeStorageException exception) {
                rollback(connection);
                if (exception instanceof KnowledgeStorageException storage) throw storage;
                throw KnowledgeStorageException.fromSql((SQLException) exception);
            }
        } catch (SQLException exception) {
            throw KnowledgeStorageException.fromSql(exception);
        }
    }

    /** Restores one bounded snapshot in original chunk order. */
    public StoredKnowledgeDocument load(long identifier) {
        if (identifier <= 0) throw new IllegalArgumentException("identifier must be positive");
        try (Connection connection = open();
                PreparedStatement document = connection.prepareStatement(
                        "SELECT embedding_model, chunk_count, dimensions FROM knowledge_documents WHERE identifier = ?")) {
            document.setLong(1, identifier);
            try (ResultSet row = document.executeQuery()) {
                if (!row.next()) throw invalidState();
                String model = row.getString("embedding_model");
                int count = row.getInt("chunk_count");
                int dimensions = row.getInt("dimensions");
                List<EmbeddedChunk> chunks = loadChunks(connection, identifier, count, dimensions);
                return new StoredKnowledgeDocument(identifier, model, chunks);
            }
        } catch (SQLException exception) {
            throw KnowledgeStorageException.fromSql(exception);
        } catch (IllegalArgumentException exception) {
            throw invalidState();
        }
    }

    private void initialize() {
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                int version = version(statement);
                if (version == 0) {
                    if (hasObjects(statement)) throw invalidState();
                    statement.executeUpdate("CREATE TABLE knowledge_documents (identifier INTEGER PRIMARY KEY AUTOINCREMENT, source_name TEXT NOT NULL, embedding_model TEXT NOT NULL, chunk_count INTEGER NOT NULL, dimensions INTEGER NOT NULL)");
                    statement.executeUpdate("CREATE TABLE knowledge_chunks (document_identifier INTEGER NOT NULL, chunk_index INTEGER NOT NULL, start_code_point INTEGER NOT NULL, end_code_point INTEGER NOT NULL, content TEXT NOT NULL, embedding BLOB NOT NULL, PRIMARY KEY (document_identifier, chunk_index), FOREIGN KEY (document_identifier) REFERENCES knowledge_documents(identifier))");
                    statement.executeUpdate("PRAGMA user_version = " + CURRENT_VERSION);
                } else if (version != CURRENT_VERSION) {
                    throw invalidState();
                }
                requireSchema(statement);
                connection.commit();
            } catch (SQLException | KnowledgeStorageException exception) {
                rollback(connection);
                if (exception instanceof KnowledgeStorageException storage) throw storage;
                throw KnowledgeStorageException.fromSql((SQLException) exception);
            }
        } catch (SQLException exception) {
            throw KnowledgeStorageException.fromSql(exception);
        }
    }

    private static long insertDocument(Connection connection, String model, List<EmbeddedChunk> chunks) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO knowledge_documents (source_name, embedding_model, chunk_count, dimensions) VALUES (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, chunks.getFirst().chunk().sourceName());
            statement.setString(2, model);
            statement.setInt(3, chunks.size());
            statement.setInt(4, chunks.getFirst().dimensions());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw invalidState();
                return keys.getLong(1);
            }
        }
    }

    private static void insertChunks(Connection connection, long identifier, List<EmbeddedChunk> chunks) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO knowledge_chunks (document_identifier, chunk_index, start_code_point, end_code_point, content, embedding) VALUES (?, ?, ?, ?, ?, ?)")) {
            for (EmbeddedChunk embedded : chunks) {
                DocumentChunk chunk = embedded.chunk();
                statement.setLong(1, identifier);
                statement.setInt(2, chunk.index());
                statement.setInt(3, chunk.startCodePoint());
                statement.setInt(4, chunk.endCodePoint());
                statement.setString(5, chunk.content());
                statement.setBytes(6, encode(embedded.vector()));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static List<EmbeddedChunk> loadChunks(Connection connection, long identifier, int expectedCount, int dimensions) throws SQLException {
        if (expectedCount <= 0 || expectedCount > DocumentChunker.MAX_CHUNKS || dimensions <= 0 || dimensions > EmbeddedChunk.MAX_DIMENSIONS) throw invalidState();
        List<EmbeddedChunk> chunks = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT d.source_name, c.chunk_index, c.start_code_point, c.end_code_point, c.content, c.embedding FROM knowledge_chunks c JOIN knowledge_documents d ON d.identifier = c.document_identifier WHERE c.document_identifier = ? ORDER BY c.chunk_index LIMIT ?")) {
            statement.setLong(1, identifier);
            statement.setInt(2, DocumentChunker.MAX_CHUNKS + 1);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    DocumentChunk chunk = new DocumentChunk(rows.getString(1), rows.getInt(2), rows.getInt(3), rows.getInt(4), rows.getString(5));
                    chunks.add(new EmbeddedChunk(chunk, decode(rows.getBytes(6), dimensions)));
                }
            }
        }
        if (chunks.size() != expectedCount) throw invalidState();
        return List.copyOf(chunks);
    }

    private static void validate(String model, List<EmbeddedChunk> chunks) {
        if (model == null || model.isBlank() || model.length() > 128 || chunks == null
                || chunks.isEmpty() || chunks.size() > DocumentChunker.MAX_CHUNKS) {
            throw new IllegalArgumentException("stored document must be bounded and complete");
        }
        List<EmbeddedChunk> copy = List.copyOf(chunks);
        String source = copy.getFirst().chunk().sourceName();
        int dimensions = copy.getFirst().dimensions();
        for (int index = 0; index < copy.size(); index++) {
            EmbeddedChunk value = copy.get(index);
            if (!source.equals(value.chunk().sourceName()) || value.chunk().index() != index || value.dimensions() != dimensions) throw new IllegalArgumentException("stored chunks must be ordered and consistent");
        }
    }

    private static byte[] encode(double[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Double.BYTES).order(ByteOrder.BIG_ENDIAN);
        for (double value : vector) buffer.putDouble(value);
        return buffer.array();
    }

    private static double[] decode(byte[] bytes, int dimensions) {
        if (bytes == null || bytes.length != dimensions * Double.BYTES) throw invalidState();
        double[] vector = new double[dimensions];
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        for (int index = 0; index < dimensions; index++) vector[index] = buffer.getDouble();
        return vector;
    }

    private Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection(databaseUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout = 0");
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    private static int version(Statement statement) throws SQLException { try (ResultSet result = statement.executeQuery("PRAGMA user_version")) { return result.next() ? result.getInt(1) : -1; } }
    private static boolean hasObjects(Statement statement) throws SQLException { try (ResultSet result = statement.executeQuery("SELECT 1 FROM sqlite_schema WHERE name NOT LIKE 'sqlite_%' LIMIT 1")) { return result.next(); } }
    private static void requireSchema(Statement statement) throws SQLException {
        requireColumns(statement, "knowledge_documents", List.of(
                new Column("identifier", "INTEGER", false, true),
                new Column("source_name", "TEXT", true, false),
                new Column("embedding_model", "TEXT", true, false),
                new Column("chunk_count", "INTEGER", true, false),
                new Column("dimensions", "INTEGER", true, false)));
        requireColumns(statement, "knowledge_chunks", List.of(
                new Column("document_identifier", "INTEGER", true, true),
                new Column("chunk_index", "INTEGER", true, true),
                new Column("start_code_point", "INTEGER", true, false),
                new Column("end_code_point", "INTEGER", true, false),
                new Column("content", "TEXT", true, false),
                new Column("embedding", "BLOB", true, false)));
        try (ResultSet result = statement.executeQuery(
                "PRAGMA foreign_key_list('knowledge_chunks')")) {
            if (!result.next() || !"knowledge_documents".equals(result.getString("table"))
                    || !"document_identifier".equals(result.getString("from"))
                    || !"identifier".equals(result.getString("to"))) throw invalidState();
        }
    }

    private static void requireColumns(Statement statement, String table,
            List<Column> expected) throws SQLException {
        List<Column> actual = new ArrayList<>();
        try (ResultSet result = statement.executeQuery("PRAGMA table_info('" + table + "')")) {
            while (result.next()) {
                actual.add(new Column(result.getString("name"), result.getString("type"),
                        result.getInt("notnull") == 1, result.getInt("pk") > 0));
            }
        }
        if (!actual.equals(expected)) throw invalidState();
    }
    private static KnowledgeStorageException invalidState() { return new KnowledgeStorageException(KnowledgeStorageException.Reason.INVALID_STATE); }
    private static void rollback(Connection connection) { try { connection.rollback(); } catch (SQLException ignored) { } }
    private record Column(String name, String type, boolean notNull, boolean primaryKey) { }
}

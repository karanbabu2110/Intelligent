package io.kaos.knowledge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteKnowledgeStoreTest {
    @TempDir Path temporaryDirectory;

    @Test
    void atomicallyPersistsAndRestoresExactOrderedChunksAcrossInstances() {
        Path database = temporaryDirectory.resolve("knowledge.db");
        List<EmbeddedChunk> input = List.of(
                embedded(0, 0, 4, "text", 0.1, -0.2),
                embedded(1, 4, 8, "more", 0.3, 0.4));
        long identifier = new SqliteKnowledgeStore(database).store("embeddinggemma", input);

        StoredKnowledgeDocument restored = new SqliteKnowledgeStore(database).load(identifier);

        assertEquals("embeddinggemma", restored.embeddingModel());
        assertEquals(List.of("text", "more"), restored.embeddedChunks().stream()
                .map(value -> value.chunk().content()).toList());
        assertArrayEquals(new double[] {0.1, -0.2}, restored.embeddedChunks().getFirst().vector());
        assertArrayEquals(new double[] {0.3, 0.4}, restored.embeddedChunks().get(1).vector());
    }

    @Test
    void loadsAllDocumentsInInsertionOrderForBoundedRetrieval() {
        Path database = temporaryDirectory.resolve("all.db");
        SqliteKnowledgeStore store = new SqliteKnowledgeStore(database);
        store.store("model", List.of(embedded(0, 0, 4, "text", 1, 0)));
        store.store("model", List.of(embedded(0, 0, 4, "more", 0, 1)));

        List<StoredKnowledgeDocument> documents = new SqliteKnowledgeStore(database).loadAll();

        assertEquals(List.of(1L, 2L), documents.stream()
                .map(StoredKnowledgeDocument::identifier).toList());
        assertEquals(List.of("text", "more"), documents.stream()
                .map(document -> document.embeddedChunks().getFirst().chunk().content()).toList());
    }

    @Test
    void rejectsAnUnsupportedSchemaWithoutReplacingIt() throws Exception {
        Path database = temporaryDirectory.resolve("future.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE private_future_data (value TEXT)");
            statement.execute("PRAGMA user_version = 2");
        }

        KnowledgeStorageException failure = assertThrows(KnowledgeStorageException.class,
                () -> new SqliteKnowledgeStore(database));

        assertEquals(KnowledgeStorageException.Reason.INVALID_STATE, failure.reason());
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                Statement statement = connection.createStatement();
                var result = statement.executeQuery(
                        "SELECT name FROM sqlite_schema WHERE name = 'private_future_data'")) {
            assertEquals(true, result.next());
        }
    }

    @Test
    void rejectsInconsistentChunksBeforeCreatingPartialRows() {
        SqliteKnowledgeStore store = new SqliteKnowledgeStore(
                temporaryDirectory.resolve("invalid.db"));
        List<EmbeddedChunk> inconsistent = List.of(
                embedded(0, 0, 4, "text", 0.1, 0.2),
                embedded(2, 4, 8, "more", 0.3, 0.4));

        assertThrows(IllegalArgumentException.class,
                () -> store.store("embeddinggemma", inconsistent));
    }

    @Test
    void rollsBackTheDocumentAndEarlierChunksWhenABatchWriteFails() throws Exception {
        Path database = temporaryDirectory.resolve("rollback.db");
        SqliteKnowledgeStore store = new SqliteKnowledgeStore(database);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER reject_second_chunk BEFORE INSERT ON knowledge_chunks "
                    + "WHEN NEW.chunk_index = 1 BEGIN SELECT RAISE(ABORT, 'private failure'); END");
        }

        assertThrows(KnowledgeStorageException.class, () -> store.store(
                "embeddinggemma", List.of(
                        embedded(0, 0, 4, "text", 0.1, 0.2),
                        embedded(1, 4, 8, "more", 0.3, 0.4))));

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                Statement statement = connection.createStatement();
                var documents = statement.executeQuery("SELECT COUNT(*) FROM knowledge_documents")) {
            assertEquals(0, documents.getInt(1));
        }
    }

    private static EmbeddedChunk embedded(int index, int start, int end, String content,
            double... vector) {
        return new EmbeddedChunk(
                new DocumentChunk("notes.txt", index, start, end, content), vector);
    }
}

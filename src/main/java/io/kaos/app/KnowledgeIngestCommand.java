package io.kaos.app;

import io.kaos.ai.ollama.OllamaEmbeddingClient;
import io.kaos.ai.ollama.OllamaEmbeddingConfiguration;
import io.kaos.knowledge.DocumentChunk;
import io.kaos.knowledge.DocumentChunker;
import io.kaos.knowledge.ExtractedText;
import io.kaos.knowledge.IngestedDocument;
import io.kaos.knowledge.KnowledgeIngestionException;
import io.kaos.knowledge.KnowledgeStorageException;
import io.kaos.knowledge.KnowledgeDatabasePath;
import io.kaos.knowledge.PlainTextExtractor;
import io.kaos.knowledge.TextDocumentIngestor;
import io.kaos.knowledge.TextExtractionException;
import io.kaos.knowledge.SqliteKnowledgeStore;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Coordinates admission, extraction, chunking, and embeddings for one local text document.
 */
final class KnowledgeIngestCommand {
    private final CommandContext context;
    private final Supplier<OllamaEmbeddingConfiguration> configurationLoader;
    private final EmbeddingSubmission embeddingSubmission;
    private final KnowledgeStorageSubmission storageSubmission;

    KnowledgeIngestCommand(CommandContext context) {
        this(context, OllamaEmbeddingConfiguration::load,
                (configuration, chunks) ->
                        new OllamaEmbeddingClient().embed(configuration, chunks),
                KnowledgeIngestCommand::storeLocally);
    }

    KnowledgeIngestCommand(CommandContext context,
            Supplier<OllamaEmbeddingConfiguration> configurationLoader,
            EmbeddingSubmission embeddingSubmission) {
        this(context, configurationLoader, embeddingSubmission, (model, chunks) -> 1L);
    }

    KnowledgeIngestCommand(CommandContext context,
            Supplier<OllamaEmbeddingConfiguration> configurationLoader,
            EmbeddingSubmission embeddingSubmission,
            KnowledgeStorageSubmission storageSubmission) {
        this.context = Objects.requireNonNull(context, "context");
        this.configurationLoader = Objects.requireNonNull(configurationLoader, "configurationLoader");
        this.embeddingSubmission = Objects.requireNonNull(embeddingSubmission, "embeddingSubmission");
        this.storageSubmission = Objects.requireNonNull(storageSubmission, "storageSubmission");
    }

    int execute(String pathText) {
        try {
            IngestedDocument document = new TextDocumentIngestor().ingest(Path.of(pathText));
            ExtractedText extractedText = new PlainTextExtractor().extract(document);
            List<DocumentChunk> chunks = new DocumentChunker().chunk(extractedText);
            OllamaEmbeddingConfiguration embeddingConfiguration = configurationLoader.get();
            OllamaEmbeddingClient.Result embeddingResult = embeddingSubmission.embed(
                    embeddingConfiguration, chunks);
            if (!embeddingResult.successful()) {
                return reportEmbeddingFailure(embeddingResult.status());
            }
            int dimensions = embeddingResult.embeddedChunks().getFirst().dimensions();
            long storedIdentifier = storageSubmission.store(
                    embeddingConfiguration.modelName(), embeddingResult.embeddedChunks());
            context.output().println("Ingested document: " + document.name()
                    + " (type: " + document.mediaType()
                    + ", bytes: " + document.byteCount()
                    + ", characters: " + extractedText.codePointCount()
                    + ", chunks: " + chunks.size()
                    + ", embeddings: " + embeddingResult.embeddedChunks().size()
                    + ", dimensions: " + dimensions
                    + ", stored document: " + storedIdentifier + ").");
            return KaosApplication.SUCCESS;
        } catch (InvalidPathException exception) {
            logError(KaosApplication.INVALID_KNOWLEDGE_DOCUMENT_CODE,
                    "Expected one readable, non-empty UTF-8 .txt file. Check the file and retry.");
            return KaosApplication.APPLICATION_ERROR;
        } catch (KnowledgeIngestionException exception) {
            String code;
            String recovery;
            switch (exception.reason()) {
                case INVALID_DOCUMENT -> {
                    code = KaosApplication.INVALID_KNOWLEDGE_DOCUMENT_CODE;
                    recovery = "Expected one readable, non-empty UTF-8 .txt file. "
                            + "Check the file and retry.";
                }
                case UNAVAILABLE -> {
                    code = KaosApplication.UNAVAILABLE_KNOWLEDGE_DOCUMENT_CODE;
                    recovery = "The local text document could not be read. "
                            + "Check that it exists and is accessible, then retry.";
                }
                case TOO_LARGE -> {
                    code = KaosApplication.KNOWLEDGE_DOCUMENT_LIMIT_CODE;
                    recovery = "The local text document exceeds the 1 MiB ingestion limit. "
                            + "Choose a smaller file and retry.";
                }
                default -> throw new IllegalStateException("Unknown ingestion reason.");
            }
            logError(code, recovery);
            return KaosApplication.APPLICATION_ERROR;
        } catch (TextExtractionException exception) {
            logError(KaosApplication.KNOWLEDGE_TEXT_EXTRACTION_CODE,
                    "The admitted document could not be extracted as bounded UTF-8 text. "
                            + "Check the file content and retry.");
            return KaosApplication.APPLICATION_ERROR;
        } catch (KnowledgeStorageException exception) {
            logError(KaosApplication.KNOWLEDGE_STORAGE_CODE, storageRecovery(exception.reason()));
            return KaosApplication.APPLICATION_ERROR;
        } catch (IllegalArgumentException exception) {
            logError(KaosApplication.INVALID_OLLAMA_EMBEDDING_MODEL_CODE,
                    "Configure one installed local Ollama embedding model with "
                            + "KAOS_OLLAMA_EMBEDDING_MODEL, then retry.");
            return KaosApplication.APPLICATION_ERROR;
        } catch (IllegalStateException exception) {
            logError(KaosApplication.UNREADABLE_OLLAMA_EMBEDDING_MODEL_CODE,
                    "The local embedding model configuration could not be read. "
                            + "Check process permissions and retry.");
            return KaosApplication.APPLICATION_ERROR;
        }
    }

    private static long storeLocally(String model, List<io.kaos.knowledge.EmbeddedChunk> chunks) {
        try {
            return new SqliteKnowledgeStore(KnowledgeDatabasePath.load()).store(model, chunks);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw KnowledgeStorageException.unavailable();
        }
    }

    private static String storageRecovery(KnowledgeStorageException.Reason reason) {
        return switch (reason) {
            case LOCKED -> "The local knowledge database is locked. Close the other user and retry.";
            case CORRUPT -> "The local knowledge database is corrupt or invalid. Preserve it for recovery and select a valid data directory.";
            case READ_ONLY -> "The local knowledge database is read-only. Restore write access or select a writable data directory.";
            case CAPACITY -> "The local knowledge database cannot accept more data. Free local capacity and retry.";
            case UNAVAILABLE -> "The local knowledge database is unavailable. Check the configured data directory and retry.";
            case INVALID_STATE -> "The local knowledge database schema is unsupported. Preserve it and use a compatible database.";
            case UNKNOWN -> "The local knowledge database operation failed. Preserve the database and retry after checking local storage.";
        };
    }

    private int reportEmbeddingFailure(OllamaEmbeddingClient.Status status) {
        String code;
        String recovery;
        switch (status) {
            case UNAVAILABLE, REQUEST_FAILED -> {
                code = KaosApplication.OLLAMA_EMBEDDING_CODE;
                recovery = "Local Ollama could not generate all document embeddings. "
                        + "Check that Ollama and the configured embedding model are available, then retry.";
            }
            case TIMEOUT -> {
                code = KaosApplication.OLLAMA_EMBEDDING_TIMEOUT_CODE;
                recovery = "Local embedding generation exceeded the five-minute limit. "
                        + "Retry with a smaller document.";
            }
            case INTERRUPTED -> {
                code = KaosApplication.OLLAMA_EMBEDDING_CODE;
                recovery = "Local embedding generation was cancelled. No vectors were retained.";
            }
            case INVALID_RESPONSE, LOCAL_LIMIT_REACHED -> {
                code = KaosApplication.OLLAMA_EMBEDDING_RESPONSE_CODE;
                recovery = "Ollama returned an invalid or oversized embedding response. "
                        + "Check the configured embedding model and retry.";
            }
            default -> throw new IllegalStateException("Unexpected embedding status: " + status);
        }
        logError(code, recovery);
        return KaosApplication.APPLICATION_ERROR;
    }

    private void logError(String code, String message) {
        ErrorReporter.report(context.errorOutput(), code, message);
    }
}

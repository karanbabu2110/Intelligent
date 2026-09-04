package io.kaos.app;

import io.kaos.ai.ollama.OllamaEmbeddingClient;
import io.kaos.ai.ollama.OllamaEmbeddingConfiguration;
import io.kaos.knowledge.DocumentChunk;
import io.kaos.knowledge.KnowledgeDatabasePath;
import io.kaos.knowledge.GroundedPrompt;
import io.kaos.knowledge.GroundedPromptBuilder;
import io.kaos.knowledge.KnowledgeQuery;
import io.kaos.knowledge.KnowledgeStorageException;
import io.kaos.knowledge.RelevantContextRetriever;
import io.kaos.knowledge.RetrievedContext;
import io.kaos.knowledge.SqliteKnowledgeStore;
import io.kaos.knowledge.SourceCitation;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

/** Embeds one query and ranks compatible stored chunks without printing content. */
final class KnowledgeRetrieveCommand {
    private final CommandContext context;
    private final Supplier<OllamaEmbeddingConfiguration> configurationLoader;
    private final EmbeddingSubmission embeddingSubmission;
    private final KnowledgeDocumentLoader documentLoader;

    KnowledgeRetrieveCommand(CommandContext context) {
        this(context, OllamaEmbeddingConfiguration::load,
                (configuration, chunks) ->
                        new OllamaEmbeddingClient().embed(configuration, chunks),
                KnowledgeRetrieveCommand::loadDocuments);
    }

    KnowledgeRetrieveCommand(CommandContext context,
            Supplier<OllamaEmbeddingConfiguration> configurationLoader,
            EmbeddingSubmission embeddingSubmission,
            KnowledgeDocumentLoader documentLoader) {
        this.context = Objects.requireNonNull(context, "context");
        this.configurationLoader = Objects.requireNonNull(configurationLoader, "configurationLoader");
        this.embeddingSubmission = Objects.requireNonNull(embeddingSubmission, "embeddingSubmission");
        this.documentLoader = Objects.requireNonNull(documentLoader, "documentLoader");
    }

    int execute(String queryText) {
        try {
            KnowledgeQuery query = new KnowledgeQuery(queryText);
            OllamaEmbeddingConfiguration configuration = configurationLoader.get();
            DocumentChunk queryChunk = new DocumentChunk(
                    "query.txt", 0, 0, query.codePointCount(), query.text());
            OllamaEmbeddingClient.Result embedding = embeddingSubmission.embed(
                    configuration, List.of(queryChunk));
            if (!embedding.successful()) return reportEmbeddingFailure(embedding.status());
            List<RetrievedContext> matches;
            try {
                matches = new RelevantContextRetriever().retrieve(
                        embedding.embeddedChunks().getFirst().vector(),
                        configuration.modelName(), documentLoader.load());
            } catch (IllegalArgumentException exception) {
                report(KaosApplication.OLLAMA_EMBEDDING_RESPONSE_CODE,
                        "Ollama returned an unusable query embedding. Check the configured embedding model and retry.");
                return KaosApplication.APPLICATION_ERROR;
            }
            if (matches.isEmpty()) {
                report(KaosApplication.KNOWLEDGE_RETRIEVAL_CODE,
                        "No compatible stored context is available. Ingest a document with the configured embedding model and retry.");
                return KaosApplication.APPLICATION_ERROR;
            }
            GroundedPrompt groundedPrompt;
            try {
                groundedPrompt = new GroundedPromptBuilder().build(query, matches);
            } catch (IllegalArgumentException exception) {
                report(KaosApplication.KNOWLEDGE_GROUNDED_PROMPT_CODE,
                        "The retrieved context could not form a safe bounded prompt. Check stored document content and retry.");
                return KaosApplication.APPLICATION_ERROR;
            }
            context.output().println("Retrieved context: " + matches.size() + " matches.");
            for (RetrievedContext match : matches) {
                context.output().printf(Locale.ROOT,
                        "document: %d, source: %s, chunk: %d, score: %.6f%n",
                        match.documentIdentifier(), printable(match.chunk().sourceName()),
                        match.chunk().index(), match.score());
            }
            context.output().println("Grounded prompt: " + groundedPrompt.codePointCount()
                    + " characters from " + groundedPrompt.contexts().size() + " contexts.");
            List<SourceCitation> citations = groundedPrompt.citations();
            context.output().println("Citation sources: " + citations.size() + ".");
            for (SourceCitation citation : citations) {
                context.output().printf("citation %s: document: %d, source: %s, chunk: %d%n",
                        citation.label(), citation.documentIdentifier(), citation.printableSourceName(),
                        citation.chunkIndex());
            }
            return KaosApplication.SUCCESS;
        } catch (KnowledgeStorageException exception) {
            report(KaosApplication.KNOWLEDGE_STORAGE_CODE,
                    "Stored knowledge could not be read safely. Check the local knowledge database and retry.");
            return KaosApplication.APPLICATION_ERROR;
        } catch (IllegalArgumentException exception) {
            report(KaosApplication.KNOWLEDGE_RETRIEVAL_CODE,
                    "Expected one non-empty knowledge query of at most 1,000 Unicode characters.");
            return KaosApplication.APPLICATION_ERROR;
        } catch (IllegalStateException exception) {
            report(KaosApplication.UNREADABLE_OLLAMA_EMBEDDING_MODEL_CODE,
                    "The local embedding model configuration could not be read. Check process permissions and retry.");
            return KaosApplication.APPLICATION_ERROR;
        }
    }

    private int reportEmbeddingFailure(OllamaEmbeddingClient.Status status) {
        String code = status == OllamaEmbeddingClient.Status.TIMEOUT
                ? KaosApplication.OLLAMA_EMBEDDING_TIMEOUT_CODE
                : status == OllamaEmbeddingClient.Status.INVALID_RESPONSE
                        || status == OllamaEmbeddingClient.Status.LOCAL_LIMIT_REACHED
                        ? KaosApplication.OLLAMA_EMBEDDING_RESPONSE_CODE
                        : KaosApplication.OLLAMA_EMBEDDING_CODE;
        report(code, "Local Ollama could not embed the knowledge query. Check Ollama and the configured embedding model, then retry.");
        return KaosApplication.APPLICATION_ERROR;
    }

    private static List<io.kaos.knowledge.StoredKnowledgeDocument> loadDocuments() {
        try {
            return new SqliteKnowledgeStore(KnowledgeDatabasePath.load()).loadAll();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw KnowledgeStorageException.unavailable();
        }
    }

    private static String printable(String sourceName) {
        return sourceName.replace("\\", "\\\\")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    private void report(String code, String message) {
        ErrorReporter.report(context.errorOutput(), code, message);
    }
}

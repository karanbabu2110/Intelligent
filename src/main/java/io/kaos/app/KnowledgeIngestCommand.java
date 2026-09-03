package io.kaos.app;

import io.kaos.knowledge.DocumentChunk;
import io.kaos.knowledge.DocumentChunker;
import io.kaos.knowledge.ExtractedText;
import io.kaos.knowledge.IngestedDocument;
import io.kaos.knowledge.KnowledgeIngestionException;
import io.kaos.knowledge.PlainTextExtractor;
import io.kaos.knowledge.TextDocumentIngestor;
import io.kaos.knowledge.TextExtractionException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Coordinates admission, extraction, and chunking for one local text document.
 */
final class KnowledgeIngestCommand {
    private final CommandContext context;

    KnowledgeIngestCommand(CommandContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    int execute(String pathText) {
        try {
            IngestedDocument document = new TextDocumentIngestor().ingest(Path.of(pathText));
            ExtractedText extractedText = new PlainTextExtractor().extract(document);
            List<DocumentChunk> chunks = new DocumentChunker().chunk(extractedText);
            context.output().println("Ingested document: " + document.name()
                    + " (type: " + document.mediaType()
                    + ", bytes: " + document.byteCount()
                    + ", characters: " + extractedText.codePointCount()
                    + ", chunks: " + chunks.size() + ").");
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
        }
    }

    private void logError(String code, String message) {
        ErrorReporter.report(context.errorOutput(), code, message);
    }
}

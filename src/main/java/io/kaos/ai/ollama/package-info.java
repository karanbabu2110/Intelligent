/**
 * Owns the first direct, local-only Ollama integration inside the single KAOS
 * application.
 *
 * <p>This package owns local reachability, explicit model selection, bounded
 * context and response-token configuration, explicit thinking control, and one
 * non-streamed prompt request whose thinking and final answer remain separate.
 * It reports provider length completion without presenting partial output as a
 * complete answer. It does not
 * define response streaming, a provider-neutral abstraction, or a separate
 * runtime boundary.</p>
 */
package io.kaos.ai.ollama;

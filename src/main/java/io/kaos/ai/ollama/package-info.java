/**
 * Owns the first direct, local-only Ollama integration inside the single KAOS
 * application.
 *
 * <p>This package owns local reachability, explicit model selection, bounded
 * context configuration, and one non-streamed prompt request. It does not
 * define response streaming, a provider-neutral abstraction, or a separate
 * runtime boundary.</p>
 */
package io.kaos.ai.ollama;

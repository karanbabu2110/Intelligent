/**
 * Owns the first direct, local-only Ollama integration inside the single KAOS
 * application.
 *
 * <p>This package owns local reachability, explicit model selection, bounded
 * context and response-token configuration, explicit thinking control, and one
 * streaming prompt request whose validated answer chunks are assembled into the
 * same bounded final answer. Thinking remains separate and is not displayed by
 * Task 002.04.01. The package does not define a provider-neutral abstraction or
 * a separate runtime boundary.</p>
 */
package io.kaos.ai.ollama;

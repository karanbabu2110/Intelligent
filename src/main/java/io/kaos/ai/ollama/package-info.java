/**
 * Owns the first direct, local-only Ollama integration inside the single KAOS
 * application.
 *
 * <p>This package owns local reachability, explicit model selection, bounded
 * context and response-token configuration, explicit thinking control, and one
 * streaming prompt request whose validated answer chunks are assembled into the
 * same bounded final answer. Explicitly enabled thinking produces only a
 * content-free progress signal; raw reasoning remains separate from terminal
 * output. Streaming is backpressured one publisher item at a time, bounded by
 * total and inactivity deadlines, and cancelled when the command thread is
 * interrupted or a hard local limit is reached. The package does not define a
 * provider-neutral abstraction or a separate runtime boundary.</p>
 */
package io.kaos.ai.ollama;

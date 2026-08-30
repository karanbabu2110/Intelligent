/**
 * Owns the first direct, local-only Ollama integration inside the single KAOS
 * application.
 *
 * <p>This package owns local reachability, explicit model selection, bounded
 * context and response-token configuration, explicit thinking control, and one
 * bounded streaming chat request. The request accepts an ordered conversation
 * history, appends the current prompt as the final user message, and assembles
 * validated answer chunks into the same bounded final answer. Explicitly enabled thinking produces only a
 * content-free progress signal; raw reasoning remains separate from terminal
 * output. Streaming is backpressured one publisher item at a time, bounded by
 * total and inactivity deadlines, and cancelled when the command thread is
 * interrupted or a hard local limit is reached. Failures distinguish
 * pre-response unavailability, request rejection, invalid data, accepted-stream
 * transport loss, total timeout, inactivity timeout, generation boundaries,
 * local limits, and cancellation without retaining provider details. The
 * package does not define a provider-neutral abstraction or a separate runtime
 * boundary.</p>
 */
package io.kaos.ai.ollama;

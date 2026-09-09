/**
 * Owns the first direct, local-only Ollama integration inside the single KAOS
 * application.
 *
 * <p>This package owns local reachability, explicit model selection, bounded
 * context and response-token configuration, explicit thinking control, and one
 * bounded streaming chat request. The request accepts an optional bounded
 * KAOS-controlled system instruction, an ordered conversation history, appends
 * the current prompt as the final user message, and assembles
 * validated answer chunks into the same bounded final answer. One explicit client operation may
 * instead advertise only {@code read_local_file} or only {@code http_get} and return at most one
 * validated concrete request without executing it. Matching continuation operations send the
 * assistant request and bounded result back to Ollama while advertising no further tools.
 * Conversation and web-search selection may advertise file and search together,
 * accepting only one concrete request. Ordinary prompt and knowledge requests remain tool-free.
 * Explicitly enabled thinking produces only a
 * content-free progress signal; raw reasoning remains separate from terminal
 * output. Streaming is backpressured one publisher item at a time, bounded by
 * total and inactivity deadlines, and cancelled when the command thread is
 * interrupted or a hard local limit is reached. Failures distinguish
 * pre-response unavailability, request rejection, invalid data, accepted-stream
 * transport loss, total timeout, inactivity timeout, generation boundaries,
 * local limits, and cancellation without retaining provider details. It also owns an explicit
 * embedding-model selection and bounded calls to the fixed loopback {@code /api/embed} endpoint.
 * The
 * package does not define a provider-neutral abstraction or a separate runtime
 * boundary.</p>
 */
package io.kaos.ai.ollama;

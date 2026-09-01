/**
 * Owns the conversation vocabulary implemented inside the single KAOS application.
 *
 * <p>The package currently defines immutable user and assistant messages plus ordered,
 * immutable in-memory history snapshots. A caller-owned foreground session creates
 * deterministic conversation identifiers, selects one history, and records clean
 * user/assistant turns within explicit conversation, message, and turn limits. A
 * selected snapshot can be supplied directly to the local Ollama chat client. The
 * package also contains a SQLite schema initializer, a fixed local database-path resolver,
 * and a store for durable conversation identifiers and bounded ordered message histories.
 * The application restores the newest bounded working set at conversation startup and writes
 * new identifiers plus complete clean turns directly. The package does not page older durable
 * conversations, preserve exact last selection, trim, summarize, or estimate provider tokens.</p>
 */
package io.kaos.conversation;

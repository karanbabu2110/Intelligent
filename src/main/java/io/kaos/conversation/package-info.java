/**
 * Owns the conversation vocabulary implemented inside the single KAOS application.
 *
 * <p>The package currently defines immutable user and assistant messages plus ordered,
 * immutable in-memory history snapshots. A caller-owned foreground session creates
 * deterministic conversation identifiers, selects one history, and records clean
 * user/assistant turns within explicit conversation, message, and turn limits. A
 * selected snapshot can be supplied directly to the local Ollama chat client. The
 * package also contains a SQLite schema initializer plus a store for durable conversation
 * identifiers and bounded ordered message histories; neither is connected to the foreground
 * session. The package does not choose an application database path, restore sessions, trim,
 * summarize, or estimate provider tokens.</p>
 */
package io.kaos.conversation;

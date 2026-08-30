/**
 * Owns the conversation vocabulary implemented inside the single KAOS application.
 *
 * <p>The package currently defines immutable user and assistant messages plus ordered,
 * immutable in-memory history snapshots. A caller-owned foreground session creates
 * deterministic conversation identifiers, selects one history, and records clean
 * user/assistant turns. A selected snapshot can be supplied directly to the local
 * Ollama chat client. The package does not persist conversations or define history
 * and token-window policy.</p>
 */
package io.kaos.conversation;

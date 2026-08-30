/**
 * Owns the conversation vocabulary implemented inside the single KAOS application.
 *
 * <p>The package currently defines immutable user and assistant messages plus ordered,
 * immutable in-memory history snapshots. A snapshot can be supplied directly to the
 * local Ollama chat client, but this package does not select an active conversation,
 * persist history, or define history and token-window policy.</p>
 */
package io.kaos.conversation;

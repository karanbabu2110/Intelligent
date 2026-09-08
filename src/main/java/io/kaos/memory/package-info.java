/**
 * Owns explicit, bounded user memory inside the single KAOS application.
 *
 * <p>The first implemented transition accepts only the fixed {@code answer-detail}
 * key and one of three structured values. A versioned local SQLite store makes
 * the create-only state durable across application processes without adding
 * semantic search or remote storage. Exact retrieval returns only absence or
 * one validated structured value and rejects unexpected durable state. The
 * structured value supplies one fixed bounded instruction to the one-shot
 * local AI path without becoming arbitrary prompt text.</p>
 */
package io.kaos.memory;

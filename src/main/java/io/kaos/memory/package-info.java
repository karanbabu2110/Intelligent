/**
 * Owns explicit, bounded user memory inside the single KAOS application.
 *
 * <p>The first implemented transition accepts only the fixed {@code answer-detail}
 * key and one of three structured values. A versioned local SQLite store makes
 * the create-only state durable across application processes without adding
 * semantic search or remote storage.</p>
 */
package io.kaos.memory;

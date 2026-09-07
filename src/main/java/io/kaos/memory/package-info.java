/**
 * Owns explicit, bounded user memory inside the single KAOS application.
 *
 * <p>The first implemented transition accepts only the fixed {@code answer-detail}
 * key and one of three structured values. State remains process-local until the
 * separately scoped storage feature supplies durable ownership.</p>
 */
package io.kaos.memory;

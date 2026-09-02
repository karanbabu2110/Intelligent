/**
 * Owns bounded local document admission inside the single KAOS application.
 *
 * <p>The package currently accepts one non-empty, regular, non-symbolic-link UTF-8
 * {@code .txt} file up to one MiB and returns an immutable in-memory byte snapshot with
 * safe metadata. It does not extract semantic text, persist documents, chunk content,
 * generate embeddings, store vectors, retrieve context, or call an AI provider.</p>
 */
package io.kaos.knowledge;

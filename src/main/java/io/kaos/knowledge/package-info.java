/**
 * Owns bounded local document admission inside the single KAOS application.
 *
 * <p>The package currently accepts one non-empty, regular, non-symbolic-link UTF-8
 * {@code .txt} file up to one MiB and returns an immutable in-memory byte snapshot with
 * safe metadata. The package also extracts the admitted bytes into an exact, immutable,
 * bounded Unicode text snapshot without whitespace or line-ending normalization. That
 * snapshot is split into immutable, ordered retrieval chunks of at most 1,000 Unicode code
 * points with 200 code points of overlap. The package does not persist documents or chunks,
 * generate embeddings, store vectors, retrieve context, or call an AI provider.</p>
 */
package io.kaos.knowledge;

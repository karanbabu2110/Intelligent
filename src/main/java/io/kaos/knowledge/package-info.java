/**
 * Owns bounded local document admission inside the single KAOS application.
 *
 * <p>The package currently accepts one non-empty, regular, non-symbolic-link UTF-8
 * {@code .txt} file up to one MiB and returns an immutable in-memory byte snapshot with
 * safe metadata. The package also extracts the admitted bytes into an exact, immutable,
 * bounded Unicode text snapshot without whitespace or line-ending normalization. That
 * snapshot is split into immutable, ordered retrieval chunks of at most 1,000 Unicode code
 * points with 200 code points of overlap. Each chunk may be paired with one immutable,
 * bounded local embedding vector. A versioned local SQLite store atomically persists exact
 * ordered chunks and fixed-width vector values and restores one bounded document snapshot.
 * A direct in-process retriever ranks compatible stored chunks by deterministic cosine
 * similarity and returns at most three immutable context results. A bounded prompt builder
 * combines one exact question with whole ranked evidence records, explicit grounding rules,
 * and untrusted-data instructions. Stable citation labels map included evidence to content-free
 * document, source, and chunk coordinates. The package does not submit grounded prompts,
 * generate or validate answers, or own provider transport.</p>
 */
package io.kaos.knowledge;

/**
 * Owns the first bounded tool contract inside the single KAOS application.
 *
 * <p>The package currently defines only the provider-visible {@code read_local_file}
 * function, strict one-path request decoding, one complete bounded UTF-8 result, and
 * metadata-only validation below one explicitly configured read root. Validation returns
 * an exact target fingerprint for later approval and revalidation without opening or
 * decoding file content. The package does not request approval, execute the tool, persist
 * content, or create a general tool framework.</p>
 */
package io.kaos.tool;

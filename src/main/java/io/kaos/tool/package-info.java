/**
 * Owns the first bounded tool contract inside the single KAOS application.
 *
 * <p>The package currently defines only the provider-visible {@code read_local_file}
 * function, strict one-path request decoding, and one complete bounded UTF-8 result.
 * It does not invoke a model, request approval, access the filesystem, execute the
 * tool, persist content, or create a general tool framework.</p>
 */
package io.kaos.tool;

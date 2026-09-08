/**
 * Owns the first bounded tool contract inside the single KAOS application.
 *
 * <p>The package currently defines only the provider-visible {@code read_local_file}
 * function, strict one-path request decoding, one complete bounded UTF-8 result, and
 * metadata-only validation below one explicitly configured read root. Validation returns
 * an exact target fingerprint, and the approval boundary binds one explicit local-user
 * decision and one claimable grant to that target. The executor consumes that grant,
 * revalidates and reads the exact target once with no-follow and strict UTF-8 rules, and
 * returns one complete bounded result. A content-free audit value can correlate the
 * decision and final outcome to one random per-invocation target identity without
 * retaining the path or content. Known request, permission, execution, cancellation, and
 * consumed-state failures map to fixed {@code KAOS-TOOL-READ-*} diagnostics without
 * retaining exception detail. The package does not persist content or audit values,
 * continue a model interaction, or create a general tool framework.</p>
 */
package io.kaos.tool.readlocalfile;

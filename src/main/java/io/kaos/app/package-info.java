/**
 * Owns the KAOS process boundary and composes implemented capability packages
 * into the single application.
 *
 * <p>{@code KaosApplication} owns JVM lifecycle, {@code ApplicationRuntime}
 * owns composition, {@code CommandRouter} owns CLI dispatch, and one command
 * coordinator owns each application workflow. {@code ErrorReporter} owns the
 * stable coded-error format. Product rules and infrastructure
 * behavior remain in direct capability packages such as {@code io.kaos.ai};
 * they do not accumulate in this package.</p>
 */
package io.kaos.app;

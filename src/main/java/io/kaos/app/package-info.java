/**
 * Owns the KAOS process boundary and composes implemented capability packages
 * into the single application.
 *
 * <p>{@code KaosApplication} owns JVM lifecycle, {@code ApplicationRuntime}
 * owns composition, {@code CommandRouter} owns CLI dispatch, and one command
 * coordinator owns each application workflow. {@code ErrorReporter} owns the
 * stable coded-error format. Product rules and infrastructure
 * behavior remain in direct capability packages such as {@code io.kaos.ai}
 * and {@code io.kaos.memory};
 * they do not accumulate in this package. The browser command family and its
 * Playwright runtime are grouped in {@code io.kaos.app.browser}.</p>
 */
package io.kaos.app;

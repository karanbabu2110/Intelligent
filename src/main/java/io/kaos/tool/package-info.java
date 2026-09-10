/**
 * Owns the small, statically composed tool runtime inside the single KAOS application.
 *
 * <p>Shared metadata, registry and selection do not grant authority. Concrete subpackages
 * retain resource validation, configuration, approval scope and executor behavior.
 * This is not a plugin framework; dynamic discovery is not implemented.</p>
 */
package io.kaos.tool;

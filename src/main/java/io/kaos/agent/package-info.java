/**
 * Owns one bounded agent workflow inside the single KAOS application.
 *
 * <p>One immutable, non-persistent goal can now be paired with one validated
 * three-step sequential plan. A model may propose the plan, while KAOS enforces
 * its exact evidence shape and resolves tool steps through the Epic 008 tool
 * selector. One per-step executor can now prepare the selected tool's existing
 * permission policy and consume its single-use grant without interpreting the
 * result as instructions. One in-memory execution now owns the sequential
 * cursor, overall and per-step states, and completed bounded evidence. Every
 * current tool step now exposes and delegates to its own exact Epic 008
 * approval policy. Preparation, permission, execution, interruption, and
 * model-provider failures now stop the execution with a content-free terminal
 * reason while preserving earlier completed evidence. Truthful user-facing
 * result synthesis remains the next Epic 009 feature.</p>
 */
package io.kaos.agent;

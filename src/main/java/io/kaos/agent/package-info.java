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
 * reason while preserving earlier completed evidence. One immutable terminal
 * result now distinguishes completed, failed, and cancelled runs; retains
 * bounded evidence and current sources; and permits an answer only after full
 * completion. The foreground {@code agent} command composes these boundaries,
 * records tool attempts through the existing content-free history, and sends
 * completed evidence to one no-tools synthesis request.</p>
 *
 * <p>A small deterministic freshness policy independently marks each goal as
 * not requiring, recommending, or requiring current-public evidence. Required
 * freshness is enforced during planning; empty search results cannot count as
 * verified current evidence. The Ollama planning request now carries the exact
 * bounded JSON Schema, and explicit local-project goals cannot omit local
 * evidence. Two path-free architecture phrases map to existing bounded package
 * summaries; no filesystem discovery is performed. Provider formatting remains
 * a proposal subject to the same strict Java validation.</p>
 */
package io.kaos.agent;

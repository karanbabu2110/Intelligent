/**
 * Owns one bounded agent workflow inside the single KAOS application.
 *
 * <p>One immutable, non-persistent goal can now be paired with one validated
 * three-step sequential plan. A model may propose the plan, while KAOS enforces
 * its exact evidence shape and resolves tool steps through the Epic 008 tool
 * selector without preparing or executing them. Execution, state, approval
 * coordination and results remain later Epic 009 features.</p>
 */
package io.kaos.agent;

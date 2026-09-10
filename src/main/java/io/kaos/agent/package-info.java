/**
 * Owns one bounded agent workflow inside the single KAOS application.
 *
 * <p>One immutable, non-persistent goal can now be paired with one validated
 * three-step sequential plan. A model may propose the plan, while KAOS enforces
 * its exact evidence shape and resolves tool steps through the Epic 008 tool
 * selector. One per-step executor can now prepare the selected tool's existing
 * permission policy and consume its single-use grant without interpreting the
 * result as instructions. Multi-step state, approval interaction and result
 * synthesis remain later Epic 009 features.</p>
 */
package io.kaos.agent;

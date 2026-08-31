---
name: kaos-architecture-website
description: Maintain and evolve the evidence-based KAOS architecture website under ui/architecture. Use when KAOS implementation changes architectural facts or when the architecture website structure, content, navigation, diagrams, styling, accessibility, or local workflow is changed; do not use for features that leave architecture and the website unaffected.
---

# KAOS Architecture Website

Keep `ui/architecture` an evidence-based view of current KAOS architecture.
Scale inspection, editing, and validation to the actual website delta.

## Establish only the affected truth

Reuse source, tests, issues, and decisions already inspected during the feature
workflow. Read the affected website section and the evidence proving its claim.
Read all of `ui/architecture/README.md` only when website structure, ownership,
operation, or validation rules change.

Use these states consistently:

- **Implemented and verified:** proven current repository behavior.
- **Next approved work:** active scope without implementation evidence.
- **Future candidate:** an option, not promised topology or delivery order.

Never infer implementation from an issue, directory, diagram, or old document.
Do not show planned databases, modules, queues, services, plugins, or deployment
units as existing.

## Edit only what changed

Update the site only for a changed package, dependency, integration, runtime or
data flow, configuration, security/failure boundary, deployment topology, or
architectural status. If no architectural fact changed, leave it untouched.

Change the verification date only after checking all displayed architecture.
For new-or-updated emphasis, remove it from the previous feature and apply it
only to content changed by the current feature. Name the feature visibly and do
not rely on color alone.

Keep semantic content, diagrams, links, and accessibility labels in HTML;
presentation and responsive behavior in CSS; and operating guidance in the
website README. Avoid duplicated claims, placeholders, speculative schemas,
unused JavaScript, and premature frameworks.

Keep the buildless HTML/CSS site until observed complexity requires more. Add a
page, structured data, JavaScript, components, or a build pipeline only to solve
a current navigation, duplication, interaction, reuse, or publishing problem.
Document any new website boundary and its rollback in the website README.

Diagrams must have accessible titles/descriptions and text status labels. Keep
heading order, keyboard focus, contrast, and reduced-motion support. The page
must not overflow on mobile; wide diagrams may scroll inside labeled,
keyboard-focusable containers.

## Validate proportionally

Always run `git diff --check` and verify newly added or changed local links.
Then use the smallest applicable level:

1. **Text, evidence, or status wording:** inspect the affected rendered text or
   DOM. Do not take screenshots or recheck every historical link.
2. **HTML structure, SVG, or long responsive content:** serve locally and check
   headings, labels, focusability, console errors, and desktop/mobile overflow.
   Use screenshots only when visual judgment is needed.
3. **CSS, navigation, interaction, breakpoint, or layout:** perform full
   desktop/mobile visual, keyboard, accessibility, console, and affected-link
   checks.

Reuse the feature's application validation; do not launch another identical
build for the website. Do not edit release documentation or create a tag or
release unless explicitly requested.

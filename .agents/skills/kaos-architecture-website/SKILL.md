---
name: kaos-architecture-website
description: Maintain and evolve the evidence-based KAOS architecture website under ui/architecture. Use when KAOS implementation changes architectural facts or when the architecture website structure, content, navigation, diagrams, styling, accessibility, or local workflow is changed; do not use for features that leave architecture and the website unaffected.
---

# KAOS Architecture Website

Maintain a trustworthy visual model of the KAOS application that can grow into
a richer website without turning future possibilities into present
architecture.

## Establish architectural truth

Before editing the website, read:

- `ui/architecture/README.md` and the affected website files;
- the source, build configuration, and tests that prove the architectural fact;
- the active issue under roadmap #814;
- relevant decisions under `docs/evolution`.

Classify every displayed element:

- **Implemented and verified:** current repository behavior with linked
  evidence;
- **Next approved work:** active roadmap scope whose implementation evidence is
  not complete;
- **Future candidate:** vision or option with no promised topology, technology,
  or delivery order.

Do not infer implementation from an issue, directory name, diagram, or old
document. Do not show planned packages, modules, databases, queues, services,
plugins, or deployment units as though they exist.

## Update only what changed

Running this skill does not require changing the website. Update only the files
needed to keep affected views correct, understandable, and maintainable.

An implementation change requires a website update when it changes packages,
dependencies, integrations, runtime or data flow, configuration,
security/failure boundaries, deployment topology, or architectural status. If
none changed, inspect the relevant view and leave all website files untouched.

Change the displayed verification date only after checking the complete
displayed architecture against current repository evidence. Never refresh the
date merely because one section was edited.

## Rotate current-feature emphasis

When the website visually marks content as new or updated, treat that emphasis
as transient recency metadata rather than architectural status. Before applying
it to the current feature, remove the recency treatment from the previous
feature without changing that content's implemented, next, or future status.
Apply the treatment only to context actually added or revised in the current
feature, name the feature in visible text, and never rely on color alone. Follow
the class and label contract in `ui/architecture/README.md`; do not accumulate
multiple generations of content presented as new.

## Preserve the website structure

Follow the ownership documented in `ui/architecture/README.md`:

- semantic content, diagrams, navigation, evidence, and accessibility labels
  belong in HTML;
- presentation tokens, layout, states, and responsive behavior belong in CSS;
- local operation and growth decisions belong in the website README.

Avoid duplicated architecture facts. Prefer a small targeted edit over a new
abstraction. Do not add empty folders, placeholder pages, speculative schemas,
or unused JavaScript.

## Evolve complexity deliberately

Use the lightest structure that solves the current website problem:

1. Keep the buildless HTML/CSS site while it remains clear.
2. Add a page only for a distinct architectural question that overloads the
   landing page.
3. Add shared structured data only when two or more views repeat the same facts
   and drift becomes a real risk.
4. Add JavaScript only for required navigation, filtering, search, or diagram
   interaction.
5. Add reusable components, a framework, package manager, tests, or a publishing
   pipeline only after static files create measured maintenance, state,
   interaction, or deployment problems.

When introducing a website boundary or tool, record its purpose, commands,
artifacts, validation, ownership, and rollback in
`ui/architecture/README.md`. Do not adopt a framework only because the website
may become complex later.

## Design for architectural understanding

Organize views around questions a developer or architect needs answered:

- current system context and runtime boundaries;
- capability and dependency relationships;
- request, command, event, and data flows;
- configuration, security, failure, and ownership boundaries;
- deployment topology when one actually exists;
- boundary-evolution decisions and their evidence;
- current limitations and next approved work.

Keep diagrams readable without relying on color. Every visual state needs a
text label. Provide accessible SVG titles and descriptions, semantic heading
order, keyboard-visible focus, sufficient contrast, and reduced-motion support.
On small screens, the page must not overflow horizontally; a wide diagram may
scroll only inside a labeled, keyboard-focusable container.

## Coordinate repository documentation

Update `README.md` or `docs/development/developer-guide.md` only when the website
path, purpose, prerequisites, or run workflow changes. Do not touch completed
work, release documentation, or unrelated evolution records for a website-only
presentation change.

The website explains architecture; it does not replace detailed acceptance
evidence or developer instructions.

## Validate the result

- Verify all local navigation, stylesheet, source, build, test, decision, and
  documentation links.
- Serve the repository locally using the command documented in
  `ui/architecture/README.md`.
- Render the affected pages at desktop and mobile widths.
- Check heading/navigation structure, status text, diagram accessibility,
  keyboard focus, page overflow, and browser console errors.
- Run `git diff --check`.
- Run application tests only when application behavior changed; report when a
  website-only change did not require them.

Do not create a Git tag or GitHub release unless the user explicitly requests
one.

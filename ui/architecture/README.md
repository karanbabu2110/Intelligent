# KAOS Architecture Website

This directory owns the living, evidence-based view of the KAOS application.
It is a developer and architectural-navigation surface, not a second source of
truth: source code, build configuration, tests, and current roadmap decisions
remain authoritative.

## Current structure

```text
ui/architecture/
|-- index.html
|-- README.md
`-- assets/
    `-- styles/
        `-- site.css
```

- `index.html` owns semantic content, the system architecture and request-flow
  diagrams, navigation, evidence links, and accessibility labels.
- `assets/styles/site.css` owns layout, status visuals, responsive behavior,
  and presentation tokens.
- `README.md` owns the website structure, local run workflow, growth rules, and
  maintenance contract.

No JavaScript, package manager, framework, build pipeline, or external asset is
needed for the current content.

## Run locally

The page can be opened directly, but a local HTTP server most closely matches a
future website workflow. From the repository root:

```powershell
python -m http.server 8765 --bind 127.0.0.1
```

Then open:

```text
http://127.0.0.1:8765/ui/architecture/
```

Stop the server with Ctrl+C. The command serves repository files locally and
must not be bound to a public network interface for ordinary development.

## Information architecture

The landing page answers ten questions in order:

1. What architecture is implemented and verified now?
2. Which concrete class and method handles each command branch?
3. How do an AI request, repeated answer chunks, and one final result cross the
   Ollama boundary in both directions?
4. How does the foreground conversation loop restore and change state, and how
   do persistence reads, writes, commits, and failures cross its boundary?
5. How does one local document path become a bounded immutable byte snapshot,
   and how do success and failure return?
6. What is the next approved integration but not yet implemented?
7. What context was added or updated by the current feature?
8. Which long-term capabilities preserve the vision without prescribing
   topology?
9. What evidence would justify a stronger module, repository, or service
   boundary?
10. Which repository artifacts prove each architectural claim?

Use these status meanings consistently:

| Status | Meaning |
| --- | --- |
| Implemented and verified | Current source/build behavior exists and has repository evidence |
| Next approved work | Active #814 work is authorized but its implementation evidence does not exist yet |
| Future candidate | Vision or option only; no package, module, service, order, or technology is promised |

The magenta **new or updated** treatment is a separate recency marker, not an
architectural status. It identifies only context changed by the current feature
and always names that feature in visible text.

Status must be expressed in text and visual styling; color alone is not enough.

## Update contract

Update the website in the same feature pull request when implementation changes
packages, dependencies, integrations, runtime or data flow, configuration,
security/failure boundaries, deployment topology, or architectural status.

Do not change the website merely because a feature was touched. If the
architecture did not change, inspect the relevant view and leave it unchanged.
Do not move a claim to implemented until linked source, build, test, or decision
evidence exists. Change the verified date only after comparing the complete
displayed architecture with the repository.

Before highlighting a new architecture update, remove the recency classes and
labels from the previous feature without changing its implemented, next, or
future status. Then apply the recency treatment only to context added or revised
by the current feature. The website must never accumulate multiple generations
of content presented as new.

## Growth rules

Grow the website only when present content creates a concrete maintenance or
usability problem:

1. Add another HTML page when it answers a distinct architectural question and
   the landing page can no longer present that question clearly.
2. Add a shared data file when the same architectural fact must appear in two or
   more views and manual duplication has become an accuracy risk.
3. Add JavaScript when real navigation, filtering, search, or diagram
   interaction is required; do not add it only to make the site look like an
   application.
4. Add reusable components or templates when repeated markup has produced a
   demonstrated maintenance problem.
5. Adopt a framework, package manager, test runner, or build/deployment pipeline
   only when the implemented website needs state, routing, component reuse,
   generated views, automated UI behavior, or publishing that static files can
   no longer support cleanly.

When a new boundary is introduced, document its purpose, commands, generated
artifacts, validation, ownership, and removal or rollback path here.

## Validation checklist

- Compare every architectural claim with current source, build, tests, and
  active roadmap evidence.
- Keep implemented, next, and future elements visibly and textually distinct.
- Confirm only the current feature carries the new-or-updated treatment and
  that its feature identifier is visible without relying on color.
- Verify every local evidence and navigation link.
- Render the site at desktop and mobile widths.
- Confirm the page itself has no horizontal overflow; a wide diagram may scroll
  only inside its labeled diagram container.
- Check keyboard focus, semantic headings, accessible diagram text, contrast,
  and browser console errors.
- Run `git diff --check`.

The repository-local
`kaos-architecture-website` skill contains the maintenance workflow used by
Codex for this website.

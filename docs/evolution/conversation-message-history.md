# Conversation Message History

Feature [#853](https://github.com/karanbabu2110/KAOS/issues/853) and Task
[#1078](https://github.com/karanbabu2110/KAOS/issues/1078) add the smallest
ordered history needed to retain the conversation messages implemented by
Feature 003.01.

## Implemented outcome

`ConversationHistory` is an immutable in-memory snapshot of ordered
`ConversationMessage` values. A caller can:

- start with `ConversationHistory.empty()`;
- construct a snapshot from an existing ordered list;
- append one validated message and receive a new history;
- read the stored order through an unmodifiable list.

Construction defensively copies caller-owned input. Appending copies the
current order before adding the new message, so an earlier snapshot never
changes. Null collections, null entries, and null appends fail immediately
with diagnostics that contain no message content.

## Ownership and lifecycle

The history remains in `io.kaos.conversation` inside the single foreground
application. It is a value held only by its caller: KAOS does not select an
active history, store it globally, send it to Ollama, serialize it, or persist
it. There is no mutable session manager, thread coordination, dependency,
module, repository, process, worker, event bus, endpoint, or service.

## Focused validation

```powershell
./gradlew.bat test --tests io.kaos.conversation.ConversationHistoryTest --no-daemon --warning-mode=all
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
```

The focused tests prove empty construction, insertion order, immutable append
semantics, defensive input copying, unmodifiable exposure, and content-free
null-input failures.

Verified on 2026-08-30:

- the focused `ConversationHistoryTest` class passed all six scenarios;
- the complete suite passed all 125 tests across ten test classes with no
  failures, errors, or skips;
- all 11 `verifyLocal` tasks executed successfully from a clean state.

## Deliberate limits

Task 003.02.01 does not introduce:

- conversation identifiers, creation, selection, or multiple active histories;
- history size, token, retention, truncation, eviction, or summarization policy;
- persistence, serialization compatibility, a database, cache, or files;
- Ollama request changes or multi-turn model context;
- a CLI command, mutable session manager, concurrency, background work, or retry.

Multi-Turn AI Context Feature
[#854](https://github.com/karanbabu2110/KAOS/issues/854) remains the next
approved boundary after Feature 003.02 is reviewed and merged.

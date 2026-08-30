# Conversation Domain Model

Feature [#852](https://github.com/karanbabu2110/KAOS/issues/852) begins with
Task [#1077](https://github.com/karanbabu2110/KAOS/issues/1077): define the
smallest conversation-owned vocabulary that later history and multi-turn work
can use without coupling domain terms to Ollama.

## Implemented outcome

The root Java application now contains package `io.kaos.conversation` with two
immutable domain types:

- `ConversationRole` identifies the currently evidenced participants: `USER`
  and `ASSISTANT`;
- `ConversationMessage` pairs one non-null role with non-null, nonblank content.

Valid message content is returned exactly as supplied. The type does not trim,
rewrite, serialize, log, or persist content, so valid Unicode, line breaks,
tabs, and significant outer whitespace remain intact. Invalid construction
fails immediately with a content-free diagnostic that never includes the
supplied message.

## Ownership and architecture

Conversation vocabulary belongs to `io.kaos.conversation` inside the existing
root application. The application package does not absorb these terms, and the
Ollama package remains responsible only for the local provider interaction.
There is no new module, dependency, interface, repository, process, worker,
event bus, network endpoint, or service.

## Focused validation

```powershell
./gradlew.bat test --tests io.kaos.conversation.ConversationMessageTest --no-daemon --warning-mode=all
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
```

The focused tests cover both supported roles, exact Unicode and multiline
content preservation, null role/content rejection, blank-content rejection,
and content-free failure text.

Verified on 2026-08-30:

- the focused `ConversationMessageTest` class passed all five scenarios;
- the complete suite passed all 119 tests across nine test classes with no
  failures, errors, or skips;
- all 11 `verifyLocal` tasks executed successfully from a clean state.

## Deliberate limits

Task 003.01.01 does not introduce:

- a conversation aggregate, identifier, creation, or selection;
- append, ordering, retention, retrieval, truncation, or other history behavior;
- persistence, serialization compatibility, a database, cache, or files;
- Ollama payload changes, multi-turn model context, or a new CLI command;
- message-size or history limits beyond the essential non-null/nonblank
  invariant.

Message History Feature
[#853](https://github.com/karanbabu2110/KAOS/issues/853) remains the next
approved boundary after Feature 003.01 is reviewed and merged.

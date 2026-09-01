# Conversation Limits and Validation

Feature [#856](https://github.com/karanbabu2110/KAOS/issues/856) and Task
[#1081](https://github.com/karanbabu2110/KAOS/issues/1081) bound the selectable
foreground conversation capability delivered by Feature 003.04.

This document preserves the Feature 003.05 delivery boundary. Feature 004.05
later restores the newest bounded foreground working set from a durable
collection that can exceed eight conversations; see
[Conversation Restore](conversation-restore.md).

## Implemented outcome

One `conversation` command now permits:

- at most 8 process-local conversations;
- at most 32 clean user/assistant turns in each conversation;
- at most 65,536 Unicode code points in any stored message; and
- at most 64 messages in any immutable `ConversationHistory` snapshot.

The existing application prompt limit remains 4,096 Unicode code points, and
the existing Ollama client still bounds serialized requests to 1 MiB. The
larger stored-message limit matches the maximum validated assistant answer, so
a clean provider result that passes the existing response boundary can be
recorded without truncation.

## Admission and state behavior

KAOS checks selected-history capacity before loading model configuration or
contacting Ollama. A prompt that would become the thirty-third turn is not sent,
streamed, or retained. Existing history remains unchanged.

Creating a ninth conversation is also rejected without changing the selected
conversation or advancing the deterministic identifier sequence. Limit
guidance never includes prompt or message content. The user can select an
existing conversation with capacity or exit and restart the ephemeral session.

The session continues after these recoverable control-state rejections. They do
not turn an otherwise clean `/exit` into an application failure because no
provider request or state mutation began.

## Ownership and implementation

The limits remain compile-time safety constants in `io.kaos.conversation`:

- `ConversationMessage` owns the per-message Unicode limit;
- `ConversationHistory` owns the immutable snapshot message limit; and
- `ConversationSession` owns conversation count and complete-turn admission.

No configuration object, policy interface, service, module, cache, worker, or
dependency was introduced. `KaosApplication` only checks the conversation-owned
capacity and prints safe recovery guidance.

The Ollama request encoder also recognizes a request-limit exception wrapped by
Jackson while serializing several individually valid messages. This preserves
the existing `LOCAL_LIMIT_REACHED` outcome and prevents any network connection
for an oversized assembled history.

## Deterministic validation

```powershell
./gradlew.bat test --tests io.kaos.conversation.ConversationMessageTest --tests io.kaos.conversation.ConversationHistoryTest --tests io.kaos.conversation.ConversationSessionTest --tests io.kaos.app.KaosApplicationTest --tests io.kaos.ai.ollama.OllamaPromptClientTest
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
```

The focused tests prove exact Unicode boundaries, immutable-history bounds,
eighth/ninth conversation behavior, thirty-second/thirty-third turn behavior,
pre-provider rejection, content-free diagnostics, and the composed serialized
request limit. The clean repository gate completed all 11 tasks and 146 tests
with no failures, errors, or skipped tests.

## Deliberate limits

This feature does not add:

- persistence, restart recovery, deletion, rename, import, or export;
- trimming, eviction, retention, summarization, or automatic history rollover;
- token estimation or a tokenizer/provider dependency;
- configurable conversation limits; or
- concurrency, a remote provider, framework, module, repository, service,
  worker, or event bus.

Unicode code points and complete turns are deterministic local storage bounds;
they are not estimates of provider tokens. The configured Ollama context window
and serialized-request limit remain the request-time safeguards. Conversation
Tests Feature [#857](https://github.com/karanbabu2110/KAOS/issues/857) is the
active boundary after Feature 003.05 was delivered by
[PR #31](https://github.com/Knowledge-Autonomous-Operating-System/KAOS/pull/31).
Its end-to-end evidence is recorded in
[Conversation tests](conversation-tests.md).

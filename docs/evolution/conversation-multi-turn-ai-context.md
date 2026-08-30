# Multi-Turn AI Context

Feature [#854](https://github.com/karanbabu2110/KAOS/issues/854) and Task
[#1079](https://github.com/karanbabu2110/KAOS/issues/1079) connect the existing
ordered in-memory message history to local Ollama without introducing a new
runtime boundary.

## Implemented outcome

`OllamaPromptClient` now sends a bounded streaming `POST /api/chat` request.
Its history-aware submission path:

1. reads an immutable `ConversationHistory` snapshot in stored order;
2. maps `USER` to Ollama `user` and `ASSISTANT` to Ollama `assistant`;
3. appends the current `OllamaPrompt` once as the final `user` message;
4. sends the complete ordered `messages` array to the fixed loopback provider;
5. decodes streamed answer and optional thinking data from Ollama's nested
   `message` object.

The existing one-shot submission methods delegate with
`ConversationHistory.empty()`. The current `ollama-prompt` command therefore
uses the same chat transport with one final user message, while later
conversation interaction can supply earlier messages explicitly.

This request shape follows Ollama's official
[chat endpoint](https://docs.ollama.com/api/chat). Streaming remains NDJSON as
described by Ollama's [streaming documentation](https://docs.ollama.com/api/streaming),
and private thinking remains separate from answer content according to the
[thinking capability](https://docs.ollama.com/capabilities/thinking).

## Safety and failure behavior

The serialized request is capped at 1 MiB by a bounded output stream. Crossing
that ceiling returns the existing local-limit outcome before an HTTP connection
is attempted. This is a transport memory and byte guard, not a conversation
selection, retention, message-count, or token-window policy.

The response keeps the existing safeguards:

- fixed loopback-only HTTP with redirects disabled;
- strict UTF-8 reconstruction across split publisher reads;
- validated assistant-role chat records;
- answer chunks emitted immediately and exactly once;
- separate bounded thinking and answer accumulation;
- required terminal reason and completion metrics;
- byte, text, total-time, inactivity, interruption, and cancellation bounds;
- content-free failure results that do not retain prompts, history, provider
  records, or reasoning.

## Deterministic validation

```powershell
./gradlew.bat test --tests io.kaos.ai.ollama.OllamaPromptClientTest --tests io.kaos.app.KaosOllamaIntegrationTest
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
```

The loopback tests prove ordered role mapping, final-prompt placement, empty
history compatibility, bounded request serialization, incremental chat answer
streaming, UTF-8 split-read preservation, thinking isolation, terminal
validation, interruption, deadlines, malformed data, incomplete streams, and
privacy-safe application behavior without requiring Ollama or external network
access.

Verified on 2026-08-30:

- the focused client and application loopback suites passed all 28 scenarios;
- the complete suite passed all 127 tests across ten test classes with no
  failures, errors, or skips;
- all 11 `verifyLocal` tasks executed successfully from a clean state;
- repository-relative documentation links and `git diff --check` passed;
- the architecture site rendered at 1440×900 and 390×844 with no page overflow
  or duplicate IDs, and both diagrams remained labeled and keyboard-focusable.

## Deliberate limits

Task 003.03.01 does not introduce:

- conversation identifiers, creation, selection, or an interactive loop;
- persistence, serialization compatibility, or restart recovery;
- message-count, token estimation, context selection, trimming, or summarization;
- automatic retry, a remote provider, provider abstraction, framework, module,
  repository, service, worker, or event bus.

Conversation Creation and Selection Feature
[#855](https://github.com/karanbabu2110/KAOS/issues/855) remains the next
approved boundary after Feature 003.03 is reviewed and merged.

# Conversation Tests

Feature [#857](https://github.com/karanbabu2110/KAOS/issues/857) and Task
[#1082](https://github.com/karanbabu2110/KAOS/issues/1082) add the final
conversation-level integration evidence for Conversation Capability Epic #9.

## Demonstrable outcome

The deterministic `KaosOllamaIntegrationTest` now drives the complete
`conversation` command through the production `OllamaPromptClient`, ephemeral
loopback HTTP, and streamed NDJSON. It proves that:

- separate conversations send isolated histories;
- selecting an earlier conversation restores only its ordered clean turns;
- each current prompt is appended exactly once to the selected context;
- validated answer chunks reach terminal output through the real application
  path; and
- a partially displayed malformed response is reported safely but is not
  retained in the next provider request.

The loopback server accepts a deterministic sequence of scripted responses and
captures every request for ordered JSON assertions. Existing one-shot success,
unavailability, and malformed-stream tests continue to use the same fixture.

## Architecture and privacy

This task changes test evidence, not the production architecture. It introduces
no production type, dependency, framework, source set, module, service, worker,
event bus, external network call, or persistent state.

The failure scenario asserts that diagnostics contain neither the failed user
prompt nor malformed provider content. Only clean user/assistant pairs are
eligible for later context, matching the existing conversation ownership rule.

## Deterministic validation

```powershell
./gradlew.bat test --tests 'io.kaos.app.KaosOllamaIntegrationTest' --no-daemon --warning-mode=all
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
```

The focused suite contains five loopback scenarios and passed. The clean
repository checkpoint completed all 11 tasks and 148 tests with no failures,
errors, or skipped tests. These checks require no Ollama installation, model,
fixed provider port, or external network; they also verify compilation,
packaging, and the existing `status` and `help` smoke commands.

## Deliberate limits and next checkpoint

This evidence does not add persistence, restart recovery, concurrency,
provider-token estimation, trimming, summarization, deletion, rename, import,
or export. Local Persistence Epic
[#823](https://github.com/karanbabu2110/KAOS/issues/823) and its first Feature
[#859](https://github.com/karanbabu2110/KAOS/issues/859) remain the next
approved checkpoint after Feature 003.06 is reviewed and merged.

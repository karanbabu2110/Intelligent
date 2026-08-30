# Conversation Creation and Selection

Feature [#855](https://github.com/karanbabu2110/KAOS/issues/855) and Task
[#1080](https://github.com/karanbabu2110/KAOS/issues/1080) make the existing
conversation history and Ollama chat-context path usable from one foreground
command.

## Implemented outcome

Run the interactive command after explicitly configuring an installed model:

```powershell
$env:KAOS_OLLAMA_MODEL = "qwen3:4b-instruct"
./gradlew.bat run --args=conversation
```

KAOS creates and selects conversation `1`. Any nonblank line that is not a
control is submitted as a prompt with the selected history. After clean Ollama
completion, the validated user prompt and final assistant answer are appended
exactly once to that conversation. The next prompt therefore includes the
earlier clean turns.

The foreground controls are:

| Control | Behavior |
| --- | --- |
| `/new` | Create and select the next numeric conversation identifier |
| `/select <id>` | Select an existing process-local conversation |
| `/list` | List identifiers in creation order; `*` marks the selected one |
| `/help` | Display the conversation controls |
| `/exit` | End the command and discard every conversation |

Unknown or malformed controls produce content-free guidance and leave the
selected conversation unchanged. End-of-input exits cleanly. Gradle's `run`
task forwards standard input so the command works through the repository's
normal local run workflow.

## Ownership and lifecycle

`ConversationSession` lives in `io.kaos.conversation` and is owned by one
foreground `KaosApplication` invocation. It uses deterministic positive numeric
identifiers and a creation-ordered in-memory collection. Selection changes only
which immutable `ConversationHistory` snapshot is read and updated.

There is no global session, static conversation state, concurrent access,
background work, file, cache, database, network API, or service. Every
conversation disappears when `/exit`, end-of-input, or process termination ends
the command.

## Clean-turn and failure rule

KAOS appends a turn only after the Ollama client reports clean completion. A
rejected, malformed, limited, timed-out, interrupted, or partially displayed
response is not retained. The user may continue after a non-interruption
failure; the final command exit remains nonzero so automation can detect that
the session encountered a failed request.

The existing request, response, UTF-8, thinking-isolation, timeout,
cancellation, and privacy-safe error boundaries remain in force. Prompts are no
longer command-line arguments in conversation mode, but they remain in process
memory and may be visible in terminal output or local provider state.

## Deterministic validation

```powershell
./gradlew.bat test --tests io.kaos.conversation.ConversationSessionTest --tests io.kaos.app.KaosApplicationTest
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
```

The focused tests prove deterministic creation, active selection, history
isolation, clean-turn ordering, actual multi-turn application context, invalid
control safety, failed-turn exclusion, end-of-input, and compatibility with the
existing one-shot commands. The clean repository gate completed all 11 tasks
and 138 tests with no failures, errors, or skipped tests.

## Deliberate limits

Task 003.04.01 does not introduce:

- persistence, restart recovery, conversation files, a database, or cache;
- names, rename, deletion, export, import, or concurrent access;
- conversation-count, message-count, token, trimming, retention, eviction, or
  summarization policy;
- retries, a remote provider, framework, module, repository, service, worker,
  or event bus.

Conversation Limits and Validation Feature
[#856](https://github.com/karanbabu2110/KAOS/issues/856) remains the next
approved boundary after Feature 003.04 is reviewed and merged.

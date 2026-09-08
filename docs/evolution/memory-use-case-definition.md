# Memory Use-Case Definition

Feature [006.01](https://github.com/karanbabu2110/KAOS/issues/875) defines the
smallest user-visible outcome for Epic 006 without claiming that memory runtime
behavior exists yet.

## Decision

KAOS will remember one explicit application-wide **answer-detail preference**.
The user chooses exactly one of three bounded values:

- `concise`
- `balanced`
- `detailed`

The first end-to-end use case is: explicitly create the preference, stop KAOS,
start a later `ollama-prompt` request, and have KAOS include the stored preference
in that local AI request. Absence preserves today's prompt behavior.

This is the first memory because it is useful across otherwise independent
requests, has a small deterministic state space, and does not require KAOS to
infer or retain a name, identity, conversation statement, document fact, or
arbitrary instruction.

## Product contract

| Concern | First-memory contract |
| --- | --- |
| User | The local person running the KAOS process |
| Key | One fixed key: `answer-detail` |
| Value | Exactly `concise`, `balanced`, or `detailed` |
| Scope | Application-wide for the configured local KAOS data directory |
| Initial state | Absent; KAOS does not invent or infer a preference |
| Creation input | A future explicit `memory-create answer-detail <value>` command |
| Creation output | Confirmation that the key was saved, without echoing unrelated state |
| AI use | A later `ollama-prompt` request receives one bounded instruction derived from the stored value |
| Inspection | `memory-inspect answer-detail` reports whether the key exists and its validated value |
| Editing | A future explicit command replaces the value atomically |
| Deletion | A future explicit command removes the key atomically and confirms absence |
| Ownership | User-controlled local application data; KAOS owns validation and persistence mechanics |
| Lifetime | Durable across process restarts until explicit edit or deletion; no implicit expiry |

The creation and inspection commands are part of this selected behavior.
Feature 006.07 will define exact edit and deletion command syntax when those
operations become active work.

## Observable lifecycle

```text
absent --explicit valid creation--> present(value)
present(value) --explicit valid edit--> present(new value)
present(value) --explicit deletion--> absent
```

- Invalid keys, values, or argument shapes fail without changing state.
- Creation while the key is already present fails without overwriting it; edit
  remains an explicit later operation.
- A persistence failure leaves the previously committed state intact.
- A retrieval failure stops the affected AI request before provider submission
  rather than silently ignoring a preference the user expects KAOS to apply.
- Cancellation or failure of an AI request never creates, edits, or deletes
  memory.

## AI-context boundary

Feature 006.05 translates each stored value into one fixed, bounded instruction
controlled by KAOS. It does not insert the database value as an arbitrary
system instruction. Deterministic integration tests verify the exact provider
request and that an absent preference leaves the existing request unchanged.

The first consumer is only `ollama-prompt`. Applying memory to interactive
conversations, grounded knowledge answers, or later capabilities is excluded
until the first path supplies implementation and user evidence. Output quality
remains model-dependent, so request construction—not a subjective prose style—
is the deterministic acceptance boundary.

## Privacy and safety

- Memory is created only by an explicit command; KAOS does not mine prompts,
  conversation history, documents, model output, or environment data.
- The selected value is structured application data, not free-form prompt text.
- Storage remains under the explicitly configured local KAOS data directory.
- The value may leave the Java process only as its fixed instruction in an
  explicitly requested call to the existing loopback-only Ollama integration.
- Normal diagnostics identify the operation and key but do not print database
  paths, SQL, provider bodies, prompts, conversation content, or other records.
- Later privacy controls must preserve explicit inspection and deletion and
  must demonstrate that no automatic collection path exists.

No credential, personal identifier, biometric attribute, health information,
location, contact, account, or document content is required for this use case.
The preference is still user-owned state and receives the same local durability,
failure, inspection, and deletion discipline as more sensitive data would.

## Relationship to existing capabilities

This memory is not conversation history: it applies across independent one-shot
requests and does not copy prior user or assistant messages. It is not RAG
knowledge: it is retrieved by one fixed key and needs no chunking, embedding,
vector storage, similarity search, or citation. Existing conversation and
knowledge databases therefore do not justify a new module, service, repository,
vector index, event bus, or background process.

Implementation starts as direct in-process application and package code. Feature
006.03 will select the smallest durable representation from current SQLite and
data-directory evidence rather than this definition preselecting a schema.

## Acceptance and validation for this feature

This decision artifact is the deliverable for Feature 006.01. Inspection must
show that it defines one user outcome, bounded inputs, observable outputs, state,
lifecycle, ownership, failure semantics, privacy constraints, one initial AI
consumer, and explicit exclusions. Documentation link checks and
`git diff --check` are proportional because this feature changes no runtime or
build behavior.

## Deliberate limits and handoff

No memory command, package, database schema, AI request change, test fixture, or
runtime claim is introduced by Feature 006.01. General facts, arbitrary
preferences, automatic extraction, multiple users or profiles, synchronization,
remote storage, expiration, ranking, and memory-driven conversation or RAG
behavior are excluded.

The next ordered feature is
[006.02 — Explicit Memory Creation](https://github.com/karanbabu2110/KAOS/issues/874),
which should implement only the absent-to-present transition defined here.

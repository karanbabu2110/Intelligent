# Memory Testing and Evaluation

Feature [006.09](https://github.com/karanbabu2110/KAOS/issues/882) supplies one
deterministic evaluation of the complete first-memory lifecycle across the real
local boundaries that matter.

## Evaluated outcome

`KaosOllamaIntegrationTest` now runs this sequence using public memory commands,
fresh store instances over one temporary version-1 SQLite database, the real
application prompt coordinator, the real Ollama HTTP client, and a loopback
NDJSON server:

```text
absent prompt -> create concise -> concise prompt -> edit detailed
-> inspect/privacy -> detailed prompt -> delete -> absent prompt
```

The evaluation inspects all four serialized provider requests. Before creation
and after deletion, each request contains only the unchanged user message.
After creation, the exact fixed concise system instruction precedes the user
message. After editing and another storage reopen, only that instruction changes
to the exact fixed detailed mapping. Inspection exposes the expected structured
value, while the privacy report exposes only `state=present` and not the value.

This proves the selected Epic 006 outcome: a user explicitly creates one bounded
application-wide preference, the value survives process/store boundaries, and a
later one-shot local AI request reuses it. The same evidence proves explicit
replacement and deletion return the request to its original memory-free shape.

## Evaluation boundary

The test uses a JUnit temporary directory and loopback HTTP only. It does not
contact an installed Ollama instance, external network, permanent user data, or
remote storage. It evaluates exact state transitions and provider request
construction, not subjective answer quality or whether a model follows the
instruction.

Conversation and grounded-knowledge commands remain intentionally memory-free;
their existing focused and integration tests preserve those separate histories
and prompts. No production code, schema, consumer, key, value, privacy policy,
or architecture boundary changes in this feature.

## Validation and limitations

Run the focused evaluation with:

```powershell
./gradlew.bat test --tests 'io.kaos.app.KaosOllamaIntegrationTest' --no-daemon --warning-mode=all
```

The complete checkpoint is:

```powershell
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
```

The capability still has one local profile, one fixed preference, one one-shot
AI consumer, and no encryption, ACL management, expiry, synchronization,
automatic inference, arbitrary memory, backup, or repair. Epic #10 remains open
until a separate exit review verifies all nine feature checkpoints and selects
the next ordered epic.

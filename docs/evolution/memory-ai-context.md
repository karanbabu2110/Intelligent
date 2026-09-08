# Memory Use in AI Context

Feature [006.05](https://github.com/karanbabu2110/KAOS/issues/880) makes the
first stored memory affect one deliberately narrow AI path: the one-shot
`ollama-prompt` command.

## Implemented outcome

After validating the user's prompt, `OllamaPromptCommand.execute` retrieves the
optional `answer-detail` value. A present value becomes exactly one fixed
KAOS-controlled instruction:

| Stored value | System instruction |
| --- | --- |
| `concise` | `Answer concisely and include only essential information.` |
| `balanced` | `Balance brevity with enough explanation to make the answer clear.` |
| `detailed` | `Answer in detail with relevant context and explanation.` |

`OllamaPromptClient` serializes that instruction as the first `system` message,
followed by any ordered history and the unchanged final `user` message. The
instruction is validated independently, limited to 256 Unicode code points,
and cannot contain unsafe controls. Users cannot supply arbitrary system text
through this feature.

When memory is absent, `OllamaPrompt` carries an empty instruction and the
serialized request is unchanged from the prior one-shot behavior. If memory
retrieval fails or durable state is invalid, KAOS reports content-free
`KAOS-MEMORY-002` guidance and stops before model loading or provider contact.

## Boundaries

The preference applies only to the public one-shot `ollama-prompt` execution
path. Internal `submit` calls used by `conversation` and `knowledge-ask` remain
memory-free, preserving their existing history and grounded-prompt semantics.
The feature does not change stored memory, add inspection, editing, deletion,
semantic search, embeddings, a vector database, provider abstraction, or a new
runtime boundary.

SQLite remains appropriate because this is one fixed exact key with three
structured values. No similarity query exists to justify vector storage.

## Validation

Focused tests prove all three exact mappings, bounded instruction validation,
system/history/user serialization order, absent-memory compatibility, scope
exclusion, and retrieval-failure short-circuiting. A deterministic integration
test crosses real version-1 SQLite retrieval, application composition, the real
Ollama HTTP client, loopback NDJSON, and exact request inspection.

The complete checkpoint is:

```powershell
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
```

Feature 006.06 owns user-facing memory inspection. This feature exposes no new
read command.

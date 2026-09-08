# Memory Privacy Controls

Feature [006.08](https://github.com/karanbabu2110/KAOS/issues/879) gives the
local user one content-free view of the first memory's privacy boundary:

```text
memory-privacy
```

## Implemented outcome

The command reports exactly four bounded facts:

```text
Memory privacy: collection=explicit-only; storage=local-only; ai-use=ollama-prompt-only; state=present.
```

State is either `present` or `absent`. The command obtains only presence through
the existing validated retrieval boundary and never prints the stored
`concise`, `balanced`, or `detailed` value. If durable state cannot be validated,
it fails with the existing content-free storage guidance instead of claiming a
privacy state.

`collection=explicit-only` means only `memory-create`, `memory-edit`, and
`memory-delete` mutate this memory; prompts, conversations, documents, model
output, environment data, and failures do not create or change it.
`storage=local-only` means the structured value remains in the configured local
`memory.db`. `ai-use=ollama-prompt-only` means only an explicitly requested
one-shot prompt maps it to a fixed KAOS-owned instruction sent to loopback
Ollama. Conversation and knowledge commands remain outside this consumer scope.

Inspection and deletion remain the explicit controls for seeing the structured
value and removing it. The privacy report does not replace either command and
performs no mutation, AI request, or remote call.

## Safety and limitations

The report does not expose database paths, SQL, stored values, prompts,
conversation content, knowledge content, model names, or provider responses.
Extra arguments fail without storage access or argument echoing. Filesystem
access remains governed by the local OS account and permissions of the selected
data directory; this feature does not claim encryption or ACL management.

There is still one fixed key, one local profile, and no automatic inference,
retention timer, synchronization, bulk operation, backup, repair, module,
service, worker, or event bus. Feature 006.09 owns the final cross-capability
memory evaluation and Epic 006 exit evidence.

## Validation

Focused tests cover present, absent, unavailable-storage, exact routing,
cross-process state, value non-disclosure, and content-safe argument rejection.
The complete checkpoint is:

```powershell
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
```

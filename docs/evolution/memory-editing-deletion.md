# Memory Editing and Deletion

Feature [006.07](https://github.com/karanbabu2110/KAOS/issues/881) completes the
explicit lifecycle of the fixed local `answer-detail` memory:

```text
memory-edit answer-detail <concise|balanced|detailed>
memory-delete answer-detail
```

## Implemented outcome

`memory-edit` requires an existing preference and atomically replaces its value
with one validated enum. `memory-delete` requires an existing preference and
atomically removes its sole row. Successful commands report the affected fixed
key and, for editing, the newly selected structured value. Separate process
runs observe the committed replacement or absence.

An absent preference is never created by editing, and repeated deletion fails
explicitly. Unsupported keys and values fail before mutation. Creation remains
create-only, so users must deliberately choose the command matching the desired
state transition.

SQLite version 1 already represents the complete bounded state, so this feature
adds no schema migration. Each update or deletion is one prepared statement;
validation and storage failures leave the previous committed state intact.
Diagnostics do not expose rejected values, database paths, SQL, unrelated
memory, prompts, conversations, knowledge records, or provider responses.

## Validation and boundaries

Deterministic in-memory and temporary-SQLite tests cover present and absent
transitions, invalid inputs, process-to-process durability, routing, and safe
command output. The complete checkpoint is:

```powershell
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
```

This feature does not add arbitrary memory keys, free-form values, automatic
collection, bulk deletion, expiration, remote synchronization, encryption,
backup, repair, retry, modules, services, workers, or event buses. Feature
006.08 owns broader privacy controls; Feature 006.09 owns the final memory
evaluation checkpoint.

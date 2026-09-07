# Explicit Memory Creation

Feature [006.02](https://github.com/karanbabu2110/KAOS/issues/874)
implements the first memory transition selected by Feature 006.01:

```text
absent --memory-create answer-detail <value>--> present(value)
```

## Implemented behavior

The single KAOS application accepts exactly:

```text
memory-create answer-detail <concise|balanced|detailed>
```

`CommandRouter` admits only that three-argument shape. `MemoryCreateCommand`
coordinates one direct call to `AnswerDetailMemory`, which validates the fixed
key and parses the value into the `AnswerDetail` enum. A successful transition
prints only the fixed key and bounded value. Invalid keys or values and a second
creation fail without changing the existing value; error output never echoes
untrusted input.

Feature 006.02 initially kept state within the Java process and deliberately did
not preselect a schema. Feature 006.03 now preserves the same create-only
transition across application runs in local SQLite; see
[Memory Storage](memory-storage.md).

## Privacy and failure boundary

- Creation occurs only after the explicit command; no prompt, conversation,
  document, environment value, or model output is inspected.
- Only the fixed `answer-detail` key and three structured values are admitted.
- Creation never overwrites an existing value; editing remains separate work.
- Stable `KAOS-MEMORY-001` diagnostics disclose the reason category without
  printing rejected key or value content.
- No network, file, database, retry, background task, module, or service is
  introduced.

## Validation

Focused tests cover all accepted values, invalid key and value behavior,
create-only semantics, content-safe diagnostics, exact CLI routing, the public
command path, and malformed argument rejection. The complete application
checkpoint is:

```powershell
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
```

## Deliberate limits and handoff

This feature did not itself implement persistence, retrieval, AI-context use,
inspection, editing, deletion, expiration, arbitrary keys, or free-form memory.
Feature 006.03 subsequently added persistence without broadening the key or
value domain.

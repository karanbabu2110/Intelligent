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

The state is application-wide within the current Java process. It is not yet
written to disk and therefore does not survive a normal CLI process exit. This
is deliberate: Feature 006.03 owns durable storage, and this feature does not
preselect its schema or lifecycle mechanics.

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

This feature does not implement persistence, restart recovery, retrieval,
AI-context use, inspection, editing, deletion, expiration, arbitrary keys, or
free-form memory. The next ordered feature is
[006.03 — Memory Storage](https://github.com/karanbabu2110/KAOS/issues/877),
which should make this exact create-only state durable without broadening its
key or value domain.

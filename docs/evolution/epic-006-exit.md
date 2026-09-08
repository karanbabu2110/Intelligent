# Epic 006 Exit — First Memory Capability

Epic [006](https://github.com/karanbabu2110/KAOS/issues/10) delivers one explicit
application-wide preference that survives restarts and affects one bounded local
AI path.

## Verified outcome

The single KAOS application now:

1. defines one fixed `answer-detail` key with `concise`, `balanced`, or
   `detailed` values;
2. creates the preference only through an explicit command and never overwrites
   it implicitly;
3. persists it in a version-1 local SQLite `memory.db`;
4. retrieves only absence or one validated enum across process restarts;
5. maps a present value to one fixed KAOS-owned system instruction for
   `ollama-prompt` only;
6. explicitly inspects, edits, and deletes the preference;
7. reports content-free collection, storage, AI-use, and presence policy; and
8. returns to the original memory-free provider request after deletion.

All nine Epic 006 features are closed as completed: #874–#882. Their delivery
PRs are #55–#63.

## Automated evidence

Feature 006.09 evaluates the complete lifecycle through public commands, fresh
store instances over one temporary SQLite database, application prompt
composition, the real Ollama HTTP client, and deterministic loopback NDJSON.
The four inspected provider requests prove exact absent, concise, detailed, and
absent-again message shapes without an installed model or external network.

The final feature tree passed all 11 `verifyLocal` tasks with 308 tests, zero
failures or errors, and one documented Windows symbolic-link skip. The release
tree repeats the complete clean checkpoint after applying only version and
documentation changes.

## Privacy and ownership evidence

- Memory mutation is limited to explicit create, edit, and delete commands.
- Prompts, conversations, documents, model output, environment data, and failed
  requests never create or modify memory.
- The stored value remains in the configured local data directory.
- Only `ollama-prompt` may translate it to one bounded instruction sent to fixed
  loopback Ollama; conversation and knowledge requests remain memory-free.
- Inspection is explicit, deletion is explicit, and the privacy report withholds
  the structured value.
- Diagnostics do not expose database paths, SQL, rejected values, prompts,
  conversations, knowledge content, model names, or provider bodies.

## Known limitations

- There is one local profile, one fixed preference, and one AI consumer.
- Filesystem confidentiality depends on the local OS account and configured
  directory permissions; KAOS does not manage encryption or ACLs.
- Schema version 1 has no permanent compatibility or migration promise.
- There is no automatic inference, expiry, synchronization, bulk operation,
  backup, repair, or remote memory provider.
- Evaluation asserts exact request construction, not subjective answer style or
  model compliance.

These limits do not prevent the bounded epic outcome and do not justify a new
module, service, repository, worker, event bus, or vector store.

## Next checkpoint

[Epic 007 — First Tool Integration](https://github.com/karanbabu2110/KAOS/issues/15)
is the next ordered evolutionary epic under #814. It remains inactive until the
user continues after the 1.4.0 release checkpoint.

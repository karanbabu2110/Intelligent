# Tool Execution History

Feature [008.07](https://github.com/karanbabu2110/KAOS/issues/899), under
[Epic 008](https://github.com/karanbabu2110/KAOS/issues/63), adds a bounded local
record of terminal tool attempts. Its concrete outcome is the read-only
`tool-history` command. The feature uses lifecycle snapshots from the shared
tool runtime and does not change model selection, validation, approval, grant,
or executor behavior.

## Runtime behavior

After a validated `read_local_file`, `http_get`, or `web_search` request reaches
a terminal permission outcome, its command writes one `ToolExecutionRecord`.
Records contain only:

- the stable registered tool name;
- an opaque random operation UUID;
- the approval decision, when one exists;
- the terminal outcome: `SUCCEEDED`, `DENIED`, `INVALID`, `CANCELLED`, or
  `FAILED`; and
- lifecycle start and completion timestamps.

Arguments, file paths, URLs, search queries, prompts, configuration, exception
details, file contents, HTTP bodies, and search results are never supplied to
the history boundary. Direct model answers and failures before a validated tool
permission lifecycle do not create records. Tool success is recorded before
the no-tools model continuation, so a later model failure does not relabel a
completed tool action.

The history write happens only after the existing lifecycle is terminal. A
storage failure cannot grant authority or cause execution to retry. If a write
fails after execution, KAOS reports `KAOS-TOOL-HISTORY-002`, stops the turn
without model continuation, and states that the attempt finished but was not
saved. Reading unavailable history reports `KAOS-TOOL-HISTORY-001`. Both
diagnostics omit storage paths and private data.

## Local storage and bounds

`SqliteToolExecutionHistory` owns schema version 1 in `tool-history.db`. The
default location is the Java user-home `.kaos` directory. Set
`KAOS_TOOL_HISTORY_DATA_DIRECTORY` to select another directory, or use the JVM
property `kaos.tool-history.data-directory`, which takes precedence. KAOS owns
the filename and creates the configured directory when required.

The store retains the newest 1,000 terminal records and trims older rows in the
same transaction as each insert. One read accepts at most 100 records. The CLI
shows the newest 20. Returned collections are immutable. Duplicate operation
UUIDs, unknown schema versions, invalid rows, locked/corrupt/read-only storage,
and capacity or I/O failures fail with content-free categories. KAOS does not
repair, replace, delete, upload, retry, or synchronize the database.

Run the read-only view:

```powershell
./gradlew.bat run --args=tool-history
```

An empty store prints `No tool executions recorded.` Otherwise each line shows
the completion timestamp, tool name, operation UUID, decision, and outcome in
newest-first order. Inspecting history never loads tool configuration, reads a
tool target, resolves DNS, contacts Ollama or SearXNG, or grants permission.

## Architecture boundary

`ToolExecutionRecord` is the validated terminal value.
`ToolExecutionHistory` is the small write/read boundary.
`SqliteToolExecutionHistory` is its only production implementation.
`ToolHistoryRecorder` adapts command completion to storage, while
`ToolHistoryCommand` renders the bounded read-only view. Everything remains an
in-process package inside the single application.

This is not an event bus, telemetry platform, audit-compliance system, plugin
framework, or persisted state machine. There is no filtering, pagination,
deletion command, export, aggregation, remote synchronization, background
writer, session-wide approval, or replay. Those capabilities require separate
evidence and requirements.

## Verification

Deterministic tests use temporary SQLite databases and local HTTP fixtures:

```powershell
./gradlew.bat test --tests 'io.kaos.tool.history.*' --tests 'io.kaos.app.ToolHistoryCommandTest' --tests 'io.kaos.app.CommandRouterTest' --tests 'io.kaos.app.ReadLocalFileCommandIntegrationTest' --tests 'io.kaos.app.HttpGetCommandIntegrationTest' --tests 'io.kaos.app.WebSearchIntegrationTest' --tests 'io.kaos.app.KaosApplicationTest' --no-daemon --warning-mode=all --console=plain
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all --console=plain
```

The focused checks cover persistence across reopen and application runs,
ordering, immutable exposure, schema rejection, read bounds, successful and
failed execution, denial, cancellation, privacy-safe display, routing, and
history-write failure without retry or continuation. They require no live
Ollama, SearXNG, public internet, or external API.

Current focused verification passed 114 tests across seven suites with no
failures, errors, or skips.

Final `clean verifyLocal` passed all 11 tasks in 20 seconds: 454 tests across
62 suites, 450 passed, four existing Windows symbolic-link skips, and no
failures or errors. Build, packaging, status, and help all passed.

# Application test harness

## Outcome

Feature [#844](https://github.com/karanbabu2110/KAOS/issues/844) adds one
reusable test-only harness for the current KAOS application. Tests can execute
the application boundary in-process for fast focused feedback or start the real
Java entry point in a bounded child process when `main`, process exit codes,
and operating-system streams must be proved.

All harness code lives under `src/test/java`. Production source, runtime
dependencies, commands, configuration, packaging, and architecture are
unchanged.

## Test layers

| Layer | Harness operation | Proves | Does not prove |
| --- | --- | --- | --- |
| In-process | `run`, `launch`, `capture` | command/configuration behavior, exact result and streams, injected failure paths | `main`, `System.exit`, child environment, JVM startup |
| Child JVM | `process`, `processWithApplicationName` | real `KaosApplication.main`, Java startup, exit code, stdout/stderr, environment isolation | packaged installation, shell wrapper, external service, cross-platform matrix |
| Timeout fixture | `processMain` with a short timeout | bounded wait and child termination | product cancellation or long-running capability policy |

Both execution layers return the same immutable test result: application exit
code, standard output, and standard error. Tests make assertions; the harness
does not hide expected output behind custom assertion utilities.

## In-process contract

`KaosApplicationHarness.run` supplies either the safe default configuration or
an explicit validated configuration and invokes the command boundary directly.
`launch` accepts a test configuration loader so invalid, unreadable, and
unexpected startup behavior can be exercised deterministically. `capture`
supports a focused custom invocation such as a failing output stream.

Each invocation owns fresh byte buffers and UTF-8 print streams. No result,
configuration, or stream is cached or shared between tests.

## Child-process contract

The child-JVM layer:

1. resolves `java` or `java.exe` from the active test JVM's `java.home`;
2. derives the production and test classpath entries from class code sources;
3. launches the selected test main class through `ProcessBuilder`;
4. removes inherited `KAOS_APP_NAME`, `JAVA_TOOL_OPTIONS`,
   `JDK_JAVA_OPTIONS`, and `_JAVA_OPTIONS` values;
5. adds `KAOS_APP_NAME` only when the test explicitly supplies it;
6. waits at most five seconds by default;
7. captures the real exit code, standard output, and standard error.

Removing inherited Java-option variables prevents a developer or CI host from
silently injecting a system property or JVM option into the child process.
Argument and configured values are never included in harness-generated failure
messages.

## Timeout and cleanup

If a child does not exit before its deadline, the harness first requests normal
termination, waits 200 milliseconds, then requests forced termination and
waits up to one additional second. It throws an assertion failure containing
only the controlled main-class name and timeout duration.

The timeout path is tested with a child fixture that sleeps for 30 seconds and
a 250-millisecond deadline. The test proves the harness returns with the
expected safe timeout failure instead of waiting for the fixture to finish.
If the test thread itself is interrupted, the harness requests forced child
termination, restores the thread interruption flag, and fails the test.

## Current process scenarios

Five child-JVM tests currently prove:

- default isolated status exits `0` with status only on standard output;
- an explicitly configured name reaches the real status output;
- an unknown private argument exits `2` on standard error without echoing it;
- an invalid private configuration exits `1` with `KAOS-CONFIG-001` without
  echoing it;
- a deliberately hanging fixture triggers bounded forced cleanup.

These complement the focused in-process tests; they do not replace them.

## Failure diagnostics and safety

Harness startup, classpath, stream-read, interruption, and timeout failures use
constant messages plus the controlled test class name and, where relevant, the
timeout duration or original exception. They do not include command arguments
or the explicit application-name value.

The harness starts only local Java child processes selected by test code. It
uses no shell, network, server, credential, personal data, privileged action,
product file state, container, or external service. Forced termination applies
only to the `Process` instance created by that harness invocation.

## Validation evidence

On 2026-08-19:

| Check | Result |
| --- | --- |
| Complete focused suite after refactor | 28 passed, 0 failed |
| Child-process/timeout suite, first run | 5 passed, 0 failed |
| Child-process/timeout suite, repeated run | 5 passed, 0 failed |
| Clean full lifecycle | `clean test build check` passed |
| Verified host | Microsoft Windows 10.0.26200, x64 |
| Verified Java runtime | OpenJDK 21.0.11 |
| Production changes/dependencies | none |
| Development version | Remains `0.0.2-SNAPSHOT`; no tag or release created |

Canonical verification:

```powershell
./gradlew.bat test --tests 'io.kaos.app.*' --no-daemon --rerun-tasks
./gradlew.bat test --tests 'io.kaos.app.KaosApplicationProcessTest' --no-daemon --rerun-tasks
./gradlew.bat clean test build check --no-daemon --warning-mode=all
```

## Feature 001.05 acceptance map

| Criterion | Evidence |
| --- | --- |
| One explicit outcome | Tests share one harness for application-boundary and real-process results |
| Outcome works | Existing tests migrated; five child-process scenarios pass repeatedly |
| Failure and safety proportional | Controlled environment, five-second deadline, targeted child cleanup, safe failure context |
| Focused validation | Twenty-eight total tests and repeated process suite plus clean full lifecycle |
| Documentation matches behavior | This record describes APIs, isolation, classpath, timeout, cleanup, safety, and limits |
| No future architecture prerequisite | Test-source-only JDK utility; production graph unchanged |
| Security, privacy, ownership, and control | No shell/external input, inherited injection variables removed, only owned child terminated |
| Current roadmap evidence is sufficient | Feature #844, Tasks #1057/#1058, test source, README, and this checkpoint |

## Assumptions and limitations

The harness was verified on the host and Java runtime listed above. It selects
`java.exe` on Windows and `java` elsewhere, but no Linux, macOS, alternative
architecture, or alternative JDK matrix has been run, so cross-platform support
is not claimed as verified.

The child layer assumes `java.home/bin` contains a runnable Java launcher and
that the active production/test class code sources are local classpath entries.
It reads output after the bounded process ends, which is safe for the current
small fixed output but is not a harness for unbounded streaming. It does not
drive standard input, signals, terminals, packaged start scripts, parallel
loads, performance measurements, networks, or external services.

After the Feature 001.05 pull request merges, close #1058 and #844, delete the
feature branch, and activate Feature
[#843](https://github.com/karanbabu2110/KAOS/issues/843), Local Run and
Verification Workflow. No Git tag or GitHub release is part of this handoff.

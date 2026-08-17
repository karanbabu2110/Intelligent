# Focused Gradle build workflow

## Purpose

This is the canonical developer workflow and final Feature 000.03 checkpoint.
It describes the actual one-project build implemented by Story #982 and
verified by Story #983.

- Roadmap: [#814](https://github.com/karanbabu2110/KAOS/issues/814)
- Epic: [#815](https://github.com/karanbabu2110/KAOS/issues/815)
- Feature: [#818](https://github.com/karanbabu2110/KAOS/issues/818)
- Final story: [#983](https://github.com/karanbabu2110/KAOS/issues/983)
- Graph contract: [minimal-gradle-graph.md](minimal-gradle-graph.md)
- Next feature after merge: [#819](https://github.com/karanbabu2110/KAOS/issues/819)
- First next story: [#984](https://github.com/karanbabu2110/KAOS/issues/984)

## Current build

```text
KAOS (:)
└── no subprojects
```

- Gradle wrapper: 9.1.0.
- Java toolchain: 21.
- Plugins: Gradle built-in `java` only.
- Internal and external dependencies: none.
- Application and test source: none until Feature #819.
- Generated local state: `.gradle/` and `build/`, both ignored.

This build proves a reproducible development starting point. It does not prove
a runnable KAOS application or product capability.

## Prerequisites

- A Java 21 JDK available through `JAVA_HOME` or the current environment.
- Network access on the first run if Gradle 9.1.0 is not already cached.
- No system Gradle installation is required; use the repository wrapper.

The verified Windows environment used OpenJDK 21.0.11 on Windows 11.

## Canonical commands

### Windows PowerShell

```powershell
./gradlew.bat --version
./gradlew.bat projects
./gradlew.bat dependencies
./gradlew.bat clean build
./gradlew.bat check
./gradlew.bat test
```

### Unix-like shell

```bash
./gradlew --version
./gradlew projects
./gradlew dependencies
./gradlew clean build
./gradlew check
./gradlew test
```

`gradlew` is committed with executable mode. The Unix commands are the standard
wrapper equivalents; the verified host for this feature was Windows.

## Expected results

| Command | Expected result |
| --- | --- |
| `--version` | Gradle 9.1.0 and a Java 21 launcher JVM |
| `projects` | Root project `KAOS`; `No sub-projects` |
| `dependencies` | Every Java configuration reports `No dependencies` |
| `clean build` | `BUILD SUCCESSFUL`; Java and test compilation report `NO-SOURCE`; root JAR is assembled |
| `check` | Succeeds with no tests or additional verification tasks yet |
| `test` | `BUILD SUCCESSFUL` with `test NO-SOURCE` |

No-source is an accurate current limitation, not a substitute for test
coverage. Feature #819 must add focused tests with the first application
behavior.

## Verified results

On 2026-08-17, the repository wrapper produced:

- Gradle 9.1.0 with launcher JVM 21.0.11;
- a single root project and no subprojects;
- no dependencies in any main or test configuration;
- successful `clean build check test`;
- `compileJava`, `compileTestJava`, and `test` reported `NO-SOURCE`;
- only ignored `.gradle/` and `build/` generated state;
- no Gradle warning requiring a build change.

A separate `--no-daemon --warning-mode=all` run also exited successfully.

## Build-change rule

When a feature needs a build change:

1. name the current source or test consumer;
2. add the smallest plugin, repository, or dependency needed by that consumer;
3. run the focused commands above;
4. update current documentation and limitations;
5. use packages before proposing another Gradle project.

Do not restore historical modules, convention plugins, quality platforms, or
aggregate tasks merely because they already exist in preserved history.

## Troubleshooting

- **Wrong Java version:** point `JAVA_HOME` to a Java 21 JDK and rerun
  `./gradlew.bat --version`.
- **Wrapper download failure:** verify network access to the distribution URL in
  `gradle/wrapper/gradle-wrapper.properties`; do not replace the wrapper with an
  unverified binary.
- **Unexpected subproject:** inspect `settings.gradle.kts`; the current file
  must contain only the root name.
- **Unexpected dependency:** run `dependencies` and inspect
  `build.gradle.kts`; every dependency must have a current consumer.
- **Stale local output:** run `clean`. Local `.gradle/` state may be removed
  when Gradle is not running, but it is never committed.

## Recovery

- Revert the relevant build commit to recover the prior organization-repository
  state.
- Historical build and capability evidence remains available at immutable
  revision `b9bd26dee098ef338286080042ba51f614804079` in
  `karanbabu2110/KAOS`.
- Use the [active dependency-path checkpoint](active-capability-dependency-path.md)
  before reconsidering any historical project or convention.

Recovery does not require copying the entire historical graph.

## Feature 000.03 acceptance map

| Feature criterion | Evidence and result |
| --- | --- |
| Active graph is smaller, current, and explainable | `projects` proves one root and no subprojects, replacing the 47-project main graph plus included build |
| Required functionality builds and relevant tests pass | Standard Java lifecycle succeeds; current test task succeeds with accurately recorded `NO-SOURCE` |
| Inactive capability history remains recoverable | Immutable baseline and Feature 000.02 inventory/classification links preserve all historical evidence |
| No module, repository, or service extraction is introduced | Current settings contain no subproject or included build; repository and runtime topology are unchanged |
| Validation commands and results are recorded | This guide records version, graph, dependencies, clean build, check, test, and ignored-output evidence |
| Documentation matches behavior and limitations | README, graph contract, and this guide state that the build exists but application source does not |
| Evidence uses #814 descendants and current artifacts | Planning links use #814, #815, #818, #983, #819, and #984; build files and evidence are repository-local |
| Next approved work is explicit | Merge Feature #818, then begin Feature #819 with Story #984 |

## Next action

Merge the single Feature 000.03 pull request. Then activate Feature
[#819](https://github.com/karanbabu2110/KAOS/issues/819) and Story
[#984](https://github.com/karanbabu2110/KAOS/issues/984) to select and implement
the single KAOS application entry point on this root build.

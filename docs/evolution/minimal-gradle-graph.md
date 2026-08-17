# Minimal active Gradle graph contract

## Purpose

This record defines the exact one-project Gradle target for Story 000.03.01 and
the implementation contract for Story #982. It applies the
[Feature 000.02 dependency-path decision](active-capability-dependency-path.md)
without consulting legacy planning issues.

## Target graph

```text
KAOS (:)
└── no subprojects or included builds
```

- Active Gradle projects: **1**.
- Internal project dependencies: **0**.
- Included builds and convention-plugin platforms: **0**.
- Application, AI, automation, integration, security, infrastructure, and
  shared capability projects: **0** until current code demonstrates a boundary.

The root project is retained because it is the current repository and the
future application consumer. No historical subproject has a current consumer.

## Required files

Story #982 will create only these build entry points:

| File | Required content and purpose |
| --- | --- |
| `settings.gradle.kts` | Set `rootProject.name = "KAOS"`; contain no `include(...)`, `includeBuild(...)`, plugin management, or dependency framework |
| `build.gradle.kts` | Apply Gradle's built-in `java` plugin; set group `io.kaos`, version `0.1.0-SNAPSHOT`, Java toolchain 21, and UTF-8 Java compilation |
| `gradlew`, `gradlew.bat` | Standard generated Gradle wrapper launchers |
| `gradle/wrapper/gradle-wrapper.jar` | Standard generated wrapper bootstrap binary |
| `gradle/wrapper/gradle-wrapper.properties` | Use Gradle 9.1.0 binary distribution with URL validation |
| `.gitignore` | Exclude only local `.gradle/` state and root `build/` output |

No `gradle.properties`, version catalog, repository declaration, dependency,
quality plugin, Spring plugin, custom task, CI workflow, module descriptor, or
application source is required by this story. A consuming feature adds one only
when it becomes necessary.

## Version evidence

- Current supported local runtime: OpenJDK 21.0.11.
- Preserved wrapper evidence: Gradle 9.1.0 at immutable baseline
  `b9bd26dee098ef338286080042ba51f614804079`.
- The [foundation inventory](reusable-foundation-assets.md) already classifies
  the wrapper and minimal Java conventions as reusable when the build begins.

The wrapper is generated for this repository rather than copying the old build
graph. Gradle 9.1.0 and Java 21 remain revisable when a current compatibility
problem is observed.

## Exact build contract

The intended `settings.gradle.kts` behavior is equivalent to:

```kotlin
rootProject.name = "KAOS"
```

The intended `build.gradle.kts` behavior is equivalent to:

```kotlin
plugins {
    java
}

group = "io.kaos"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
```

This provides standard `clean`, `compileJava`, `test`, `check`, and `build`
tasks without selecting an application framework or third-party dependency.

## Applied state

Story #982 implemented this contract in the organization repository:

- `settings.gradle.kts` contains only `rootProject.name = "KAOS"`;
- `build.gradle.kts` contains only the built-in Java plugin, coordinates, Java
  21 toolchain, and UTF-8 compiler setting;
- Gradle's standard `wrapper` task generated Gradle 9.1.0 wrapper scripts, JAR,
  and properties;
- the generated wrapper JAR SHA-256 matches the preserved Gradle 9.1.0 wrapper
  JAR: `76805E32C009C0CF0DD5D206BDDC9FB22EA42E84DB904B764F3047DE095493F3`;
- `.gitignore` excludes only `.gradle/` local state and root `build/` output;
- no include, included-build, project-dependency, repository, external
  dependency, framework, or custom-plugin declaration exists.

Applied validation on Windows 11 with OpenJDK 21.0.11:

- `gradlew.bat --version`: Gradle 9.1.0, launcher JVM 21.0.11;
- `gradlew.bat projects`: root project `KAOS`, no subprojects;
- `gradlew.bat clean build`: successful; compilation and tests were `NO-SOURCE`;
- `gradlew.bat test`: successful with expected `NO-SOURCE` result.

## Dependency constraints

- Do not declare project dependencies; no other project is registered.
- Do not declare external dependencies before code consumes them.
- Do not add `mavenCentral()` until the first external dependency is selected.
- Keep initial application code in the root source set.
- Use packages for organization before proposing another Gradle project.
- Reconsider a subproject only when a build, independent reuse, lifecycle,
  ownership, deployment, isolation, hardware, resilience, or scale need is
  demonstrated and documented.

## Recoverable transition

| Historical surface | Transition |
| --- | --- |
| Historical root build | Do not copy its global lifecycle, governance, report, alias, or multi-project aggregation tasks; preserve them at the immutable baseline |
| 46 historical subprojects | Keep outside the organization repository's active settings; no historical source is deleted |
| Historical `build-logic` | Do not include it; its 20 source files remain recoverable at the immutable baseline |
| Wrapper 9.1.0 | Regenerate standard wrapper files for the new root using the preserved wrapper as bootstrap evidence |
| Java conventions | Reimplement only toolchain 21 and UTF-8 directly in the root build |

Rollback is a normal Git revert of the Story #982 commit. Historical recovery
uses the immutable personal-repository commit; neither path requires restoring
all registrations.

## Story 000.03.02 implementation order

1. Add the minimal settings and root build files exactly within this contract.
2. Generate the Gradle 9.1.0 wrapper for the organization repository.
3. Inspect generated files and ensure no unrelated historical configuration
   entered the repository.
4. Run the focused validation commands below.
5. Record actual results and any deviations before completing Story #982.

## Canonical validation

```powershell
./gradlew.bat --version
./gradlew.bat projects
./gradlew.bat clean build
./gradlew.bat test
```

Expected results:

- wrapper reports Gradle 9.1.0 on Java 21;
- `projects` lists root project `KAOS` and no subprojects;
- `clean build` succeeds;
- `test` succeeds with `NO-SOURCE` until Feature #819 adds application tests;
- no `project(...)`, `include(...)`, or `includeBuild(...)` declaration exists.

## Risks and limitations

- Wrapper generation may need network access if Gradle 9.1.0 is not cached.
- A missing Java 21 installation must fail visibly rather than silently changing
  the toolchain target.
- A no-source build proves the build graph and toolchain, not a KAOS product
  capability.
- Feature #819 remains responsible for the first runnable application and its
  tests.

## Handoff

Story #982 implemented and proved the single-root graph without adopting
historical capability source. The
[focused Gradle build workflow](focused-gradle-build.md) records Story #983
verification and the final Feature 000.03 handoff.

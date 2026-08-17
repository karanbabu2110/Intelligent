# Single KAOS application entry point

## Decision

Story [#984](https://github.com/karanbabu2110/KAOS/issues/984)
designates exactly one active KAOS product entry point:

| Property | Current decision |
| --- | --- |
| Gradle project | Root project `KAOS` |
| Package | `io.kaos.app` |
| Class | `KaosApplication` |
| Source | `src/main/java/io/kaos/app/KaosApplication.java` |
| Run command | `./gradlew.bat run` on Windows; `./gradlew run` on Unix-like systems |
| Immediate purpose | Start the one evolving application that will receive KAOS capabilities |

The entry point prints `KAOS application baseline is running.` and exits
successfully. That output proves only that the application bootstrap runs. It
does not claim that an AI, memory, RAG, agent, automation, or other product
capability exists.

## Evidence for the choice

Before Story #984, the repository contained one root Java 21 Gradle project,
no production source, no subprojects, and no dependencies. There was no
competing runnable entry point to preserve or select.

The root project is therefore the smallest location for the first application.
The built-in Gradle `application` plugin supplies the canonical `run` task and
uses `io.kaos.app.KaosApplication` as its main class. It adds no external
dependency, framework, module, repository, process, or service.

## Configuration boundary

The baseline accepts no application configuration. Java and Gradle execution
settings remain build concerns; they are not KAOS product configuration.

A feature may add a command-line option, environment variable, or configuration
file only when it has a current consumer. Secrets must never be committed.
Configuration abstraction is deferred until at least one real capability needs
it.

## Canonical workflow

```powershell
./gradlew.bat run
./gradlew.bat clean build check test
```

Expected run output:

```text
KAOS application baseline is running.
```

The equivalent Unix-like commands use `./gradlew`.

## Active-entry-point rule

- `KaosApplication` is the only production class with a `main` method.
- New capabilities begin as packages used by this application.
- Supporting classes do not create parallel product entry points.
- Another executable requires a demonstrated runtime or delivery need and an
  explicit descendant issue under roadmap #814.
- Historical entry points remain Git evidence, not active products.

## Current limitations

- The application has no capability beyond deterministic startup.
- No product configuration is consumed.
- The only behavior is a startup identity, covered by one focused test added by
  Story #985; product-capability testing begins with the first capability.
- Distribution, deployment, long-running process behavior, and service
  extraction are deferred.

## Handoff

Story [#985](https://github.com/karanbabu2110/KAOS/issues/985) established the
[package-first application structure](package-first-application-structure.md)
around this entry point. Story
[#986](https://github.com/karanbabu2110/KAOS/issues/986) is next. Feature
[#819](https://github.com/karanbabu2110/KAOS/issues/819) remains active until
that story is complete and one feature-level pull request is merged.

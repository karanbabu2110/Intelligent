# Package-first application structure

## Purpose

Story [#985](https://github.com/karanbabu2110/KAOS/issues/985)
defines how the single KAOS application grows without treating every planned
capability as a module, repository, plugin, or service.

## Current source structure

```text
src/
|-- main/java/io/kaos/app/
|   |-- KaosApplication.java
|   `-- package-info.java
`-- test/java/io/kaos/app/
    `-- KaosApplicationTest.java
```

`io.kaos.app` owns the process entry point and composition root. It may create
and connect implemented capability objects, but product behavior must not
accumulate there.

The test mirrors the production package so it can verify package-scoped startup
behavior without widening the production API. The JUnit 6.1.1 BOM aligns the
test-only Jupiter and Platform launcher dependencies used by this current
consumer; the production runtime remains free of external dependencies.

## Growth rule

For a new capability named `<capability>`:

1. create `io.kaos.<capability>` in this root project when its first production
   class is implemented;
2. keep its behavior, domain terms, and consumer-specific configuration in that
   package;
3. call it directly in-process from `io.kaos.app` or another current consumer;
4. add subpackages only after multiple classes create a concrete naming or
   ownership problem;
5. do not create a Gradle module, repository, process, network contract, or
   plugin boundary by default.

There is no generic `capability`, `service`, `common`, `shared`, or global
`config` package. Those names would hide ownership before the application has
enough behavior to justify them.

## First AI integration location

The first AI integration will begin at `io.kaos.ai` when its roadmap feature
starts. The first class and its focused test will create the package; no empty
placeholder package is committed now.

Provider model names, endpoints, credentials, timeouts, or other settings will
live with the AI consumer until evidence identifies a genuinely process-wide
configuration concern. Secrets must come from an external runtime source and
must never be committed.

The application package will compose the AI capability directly. An interface
is introduced only when at least two real implementations or a demonstrated
test seam requires one.

## Why this is not a module or service

The repository currently has one application, one entry point, and one startup
behavior. There is no independent release cadence, ownership boundary, scaling
need, fault-isolation need, or incompatible dependency that would justify a
separate build or runtime.

Packages provide the needed navigation and ownership boundary at effectively no
operational cost. The
[capability boundary evolution rules](capability-boundary-evolution.md) define
the evidence required to graduate from a package to a module, repository, or
service later.

## Validation

```powershell
./gradlew.bat run
./gradlew.bat clean test build check
./gradlew.bat projects
```

Verified on 2026-08-17:

- the one application starts from `io.kaos.app`;
- the startup behavior has one passing focused test;
- the root project remains the only Gradle project;
- no product/runtime dependency is present;
- no placeholder capability package or distributed boundary exists.

## Handoff

Story [#986](https://github.com/karanbabu2110/KAOS/issues/986) now defines
observable growth and extraction triggers without extracting anything. Feature
[#819](https://github.com/karanbabu2110/KAOS/issues/819) is ready for its
feature-level pull request.

# Registered project and capability surface inventory

## Purpose

This inventory records the Gradle project graph and observable capability
surface preserved at KAOS baseline
`b9bd26dee098ef338286080042ba51f614804079`. It is the authoritative result of
Story 000.02.01 under roadmap #814.

The inventory describes evidence; it does not approve the historical graph or
assign the final present-state classifications required by Story #979.

## Evidence boundary

- Preserved checkout revision: `b9bd26dee098ef338286080042ba51f614804079`
- Main build registration source: [`settings.gradle.kts`](https://github.com/karanbabu2110/KAOS/blob/b9bd26dee098ef338286080042ba51f614804079/settings.gradle.kts)
- Main build configuration: [`build.gradle.kts`](https://github.com/karanbabu2110/KAOS/blob/b9bd26dee098ef338286080042ba51f614804079/build.gradle.kts)
- Included build: [`build-logic/`](https://github.com/karanbabu2110/KAOS/tree/b9bd26dee098ef338286080042ba51f614804079/build-logic)
- Inspection date: 2026-08-17

The preserved checkout was clean and its `HEAD` matched the immutable revision.
No historical file was copied into the organization repository.

## Graph summary

- The main build contains the root project plus **46 explicitly included
  subprojects**: 4 applications, 10 shared projects, 5 platform modules, 9 AI
  modules, 4 automation modules, 6 integration modules, 3 security modules,
  and 5 infrastructure modules.
- All 46 included project directories and `build.gradle.kts` files exist. No
  duplicate registration or custom `projectDir` mapping was found.
- `build-logic` is a separate included build, not one of the 46 subprojects.
- Six subprojects contain `src/main`: four application launchers and two
  platform implementations. Two of those projects contain tests.
- Across registered subprojects, `src/main` contains 34 Java files and 5
  resource files; `src/test` contains 5 Java files.
- Ten shared projects contain a README and build descriptor but no source.
  Thirty other subprojects contain a build descriptor only. Therefore 40 of
  the 46 included subprojects contain no implementation source.
- The graph contains three project-dependency edges:
  `kaos-server -> platform-config`,
  `kaos-server -> platform-observability`, and
  `platform-observability -> platform-config`.

## Main-build project inventory

`Main/test` counts tracked files below `src/main` and `src/test`. Every main
build project appears exactly once in this table: the root plus all 46 included
subprojects.

| Project | Main/test | Project dependencies | Observed surface |
| --- | ---: | --- | --- |
| `:` | 0/0 | Aggregates all subprojects | Root build lifecycle, reporting, governance, and developer tasks; no application behavior |
| `:applications:kaos-server` | 1/0 | `platform-config`, `platform-observability` | Spring Boot launcher only; no controller, route, or other product behavior |
| `:applications:kaos-desktop` | 1/0 | None | Spring Boot launcher only; no desktop UI code |
| `:applications:kaos-cli` | 1/0 | None | Spring Boot launcher only; no command implementation |
| `:applications:kaos-web` | 1/0 | None | Spring Boot launcher only; no controller, route, or web UI code |
| `:shared:documentation` | 0/0 | None | README and Java-library build descriptor only |
| `:shared:events` | 0/0 | None | README and Java-library build descriptor only |
| `:shared:foundation` | 0/0 | None | README and Java-library build descriptor only |
| `:shared:kernel` | 0/0 | None | README and Java-library build descriptor only |
| `:shared:mapping` | 0/0 | None | README and Java-library build descriptor only |
| `:shared:platform` | 0/0 | None | README and Java-library build descriptor only |
| `:shared:security` | 0/0 | None | README and Java-library build descriptor only |
| `:shared:serialization` | 0/0 | None | README and Java-library build descriptor only |
| `:shared:testing` | 0/0 | None | README and Java-library build descriptor only |
| `:shared:validation` | 0/0 | None | README and Java-library build descriptor only |
| `:modules:platform:platform-common` | 0/0 | None | Java-library build descriptor only |
| `:modules:platform:platform-core` | 0/0 | None | Java-library build descriptor only |
| `:modules:platform:platform-events` | 0/0 | None | Java-library build descriptor only |
| `:modules:platform:platform-config` | 20/2 | None | 19 Java types and one auto-configuration import for properties, profiles, validation, secrets, lifecycle, diagnostics, and health |
| `:modules:platform:platform-observability` | 15/3 | `platform-config` | 11 Java types, three Logback profiles, and one auto-configuration import for correlation, audit, structured logging, diagnostics, and health |
| `:modules:ai:ai-chat` | 0/0 | None | Java-library build descriptor only |
| `:modules:ai:ai-memory` | 0/0 | None | Java-library build descriptor only |
| `:modules:ai:ai-context` | 0/0 | None | Java-library build descriptor only |
| `:modules:ai:ai-rag` | 0/0 | None | Java-library build descriptor only |
| `:modules:ai:ai-reasoning` | 0/0 | None | Java-library build descriptor only |
| `:modules:ai:ai-planning` | 0/0 | None | Java-library build descriptor only |
| `:modules:ai:ai-agents` | 0/0 | None | Java-library build descriptor only |
| `:modules:ai:ai-tools` | 0/0 | None | Java-library build descriptor only |
| `:modules:ai:ai-models` | 0/0 | None | Java-library build descriptor only |
| `:modules:automation:automation-desktop` | 0/0 | None | Java-library build descriptor only |
| `:modules:automation:automation-browser` | 0/0 | None | Java-library build descriptor only |
| `:modules:automation:automation-workflow` | 0/0 | None | Java-library build descriptor only |
| `:modules:automation:automation-scheduler` | 0/0 | None | Java-library build descriptor only |
| `:modules:integrations:integration-email` | 0/0 | None | Java-library build descriptor only |
| `:modules:integrations:integration-calendar` | 0/0 | None | Java-library build descriptor only |
| `:modules:integrations:integration-storage` | 0/0 | None | Java-library build descriptor only |
| `:modules:integrations:integration-slack` | 0/0 | None | Java-library build descriptor only |
| `:modules:integrations:integration-github` | 0/0 | None | Java-library build descriptor only |
| `:modules:integrations:integration-web` | 0/0 | None | Java-library build descriptor only |
| `:modules:security:security-auth` | 0/0 | None | Java-library build descriptor only |
| `:modules:security:security-secrets` | 0/0 | None | Java-library build descriptor only |
| `:modules:security:security-audit` | 0/0 | None | Java-library build descriptor only |
| `:modules:infrastructure:infrastructure-persistence` | 0/0 | None | Java-library build descriptor only |
| `:modules:infrastructure:infrastructure-vector-store` | 0/0 | None | Java-library build descriptor only |
| `:modules:infrastructure:infrastructure-messaging` | 0/0 | None | Java-library build descriptor only |
| `:modules:infrastructure:infrastructure-caching` | 0/0 | None | Java-library build descriptor only |
| `:modules:infrastructure:infrastructure-monitoring` | 0/0 | None | Java-library build descriptor only |

## Included build surface

`settings.gradle.kts` includes `build-logic` as a composite build. Its 20
tracked source files implement 15 convention plugins and 5 custom task types
covering Java, Spring, testing, formatting, static analysis, packaging,
dependency governance, optimization, metadata, and reporting. This is proven
build infrastructure, but it is not an application capability or a registered
subproject in the main build.

## Runnable entry points and externally visible surfaces

The four application projects each declare a Spring Boot `mainClass` and contain
one matching `@SpringBootApplication` class:

- `applications/kaos-server/.../KaosServerApplication.java`
- `applications/kaos-desktop/.../KaosDesktopApplication.java`
- `applications/kaos-cli/.../KaosCliApplication.java`
- `applications/kaos-web/.../KaosWebApplication.java`

They are launch surfaces, not demonstrated server, desktop, CLI, or web
capabilities. Tracked Java source contains no REST controller, request mapping,
command runner, Spring Shell component, JavaFX/Swing UI, or persistence entity.

The only implemented library surfaces are Spring auto-configuration imports
for `KaosConfigurationAutoConfiguration` and `LoggingAutoConfiguration`, backed
by the platform-config and platform-observability Java and test sources listed
above. Their public types are reusable evidence, not proof that the new
application currently consumes them.

## Reproducible inspection

The inventory was produced with read-only commands equivalent to:

```powershell
git -C <preserved-checkout> status --porcelain
git -C <preserved-checkout> rev-parse HEAD
Get-Content <preserved-checkout>/settings.gradle.kts
git -C <preserved-checkout> ls-files
Get-ChildItem <preserved-checkout> -Recurse -Filter build.gradle.kts
Get-ChildItem <project>/src/main -Recurse -File
Get-ChildItem <project>/src/test -Recurse -File
Select-String -Pattern 'project\("(:[^"\)]+)"\)'
Select-String -Pattern '@SpringBootApplication|static void main'
```

Cross-checks confirmed 46 unique `include(...)` entries, 46 existing project
directories, 46 project build descriptors, 47 unique main-build table rows,
four entry points, three project-dependency edges, and no duplicate table row.

## Limitations

- This story inspected files and registrations; it did not execute the
  historical Gradle build or claim that a launcher starts successfully today.
- File presence and public types do not prove complete product behavior.
- External-library dependency details are recorded in the build files but are
  not project-to-project graph edges.
- Build output and ignored local cache files were excluded; only tracked source
  and configuration count as evidence.
- Final classifications and conflicts belong to Story #979.

## Handoff

Story #979 is next. It will assign exactly one approved present-state
classification to every main-build project, the included build, and each
identified capability surface using this inventory as evidence.

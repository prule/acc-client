# Contributing

## Prerequisites

- JDK 21 (Temurin recommended — matches CI).
- Network access for Gradle to fetch dependencies on first build (acc-messages comes from JitPack).

The Gradle wrapper handles the toolchain — no need to install Gradle separately.

## Build

```bash
./gradlew build
```

Build runs compile + tests + ktfmt check. Output JARs land in `build/libs/`.

## Test

```bash
./gradlew test                                                # all tests
./gradlew test --tests com.github.prule.acc.client.ContextUpdaterTest  # one class
./gradlew test --tests "*Session*"                            # pattern
./gradlew test --info                                         # verbose
```

Reports: `build/reports/tests/test/index.html`. CI runs the same target — see `.github/workflows/build-and-test.yml`.

## Run locally

```bash
./gradlew runAccSimulator   # bundled playback CSV → fake ACC server on port 9000
./gradlew runAccClient      # connects to localhost:9000, prints messages
```

These are JavaExec tasks defined in `build.gradle.kts`. Edit the `main()` functions in `AccClient.kt` / `AccSimulator.kt` to point at a real ACC server or a different recording.

## Code style — ktfmt (Google)

Enforced via `com.ncorti.ktfmt.gradle`. Two-space indent, automatic trailing-comma management, Google-style line breaking.

```bash
./gradlew ktfmtCheck       # fail if anything is mis-formatted
./gradlew ktfmtFormat      # rewrite files in place
```

Run `ktfmtFormat` before committing. CI fails on style drift.

## Publishing locally

```bash
./gradlew publishToMavenLocal
```

Produces `~/.m2/repository/com/github/prule/acc-client/main-SNAPSHOT/...`. Useful when developing a downstream project against an unreleased change.

## Project layout

```
src/main/kotlin/com/github/prule/acc/client/    Library code
src/main/kotlin/com/github/prule/acc/client/simulator/   Simulator (test harness)
src/main/resources/com/github/prule/acc/client/          Bundled CSV fixtures + logback.xml
src/test/kotlin/...                              Tests
docs/                                            Reference documentation
recordings/                                      Local capture output (git-ignored)
```

See `docs/Architecture.md` for module-by-module breakdown.

## Conventions

| Aspect | Convention |
|---|---|
| Package | Single flat package `com.github.prule.acc.client`, plus `simulator` subpackage. Don't introduce deeper nesting unless there's a clear reason. |
| Listeners | Implement `MessageListener<T>` or `SessionEventListener`. See `docs/ListenerRecipes.md`. |
| State | Mutate `ClientContext` ONLY from `ContextUpdater`. New fields → add to `ClientContext` + decode in `ContextUpdater`. |
| Logging | slf4j via `LoggerFactory.getLogger(javaClass)`. Default level is `debug` (see `logback.xml`). Don't `println` in library code. |
| Tests | Prefer mockk for new tests. Use real recorded bytes from the playback CSV when testing parser-dependent code. See `docs/Testing.md`. |
| Public API | The library exposes `acc-messages` via `api(...)` — adding a new public field/method is part of the API surface. |

## Pull requests

1. Branch from `main`.
2. Make the change. Add or update tests. Run `./gradlew ktfmtFormat build`.
3. Update relevant docs under `docs/` and `CHANGELOG.md` (`[Unreleased]` section).
4. Open a PR against `main`. CI will run build + test. PRs are reviewed by the maintainer.

Renovate handles dependency-version PRs automatically — don't worry about updating those by hand.

## Releasing

JitPack tracks `main` and tagged releases. To cut a release:

1. Move `[Unreleased]` entries in `CHANGELOG.md` under a new `[x.y.z] - YYYY-MM-DD` section.
2. Tag the commit (`git tag x.y.z && git push --tags`).
3. JitPack will pick the tag up on first request and build a JAR.

There is no separate `version` bump in `build.gradle.kts` (currently pinned to `main-SNAPSHOT`); tagging is the release mechanism.

## Reporting issues

Use GitHub Issues. Include:

- ACC version + broadcasting.json snippet (port + that you have a `connectionPassword` set — don't paste the password itself).
- A minimal reproducer (listener wiring + the message you saw misbehave).
- A captured CSV recording if the issue is parser-related — bytes are the source of truth.

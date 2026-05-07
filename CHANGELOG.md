# Changelog

All notable changes to this project are recorded here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project tracks `main-SNAPSHOT` via [JitPack](https://jitpack.io/#prule/acc-client). Tagged releases will be added here when they are cut.

## [Unreleased]

### Added

- `ClientContext` — caller-owned cross-session state. Holds `connectionId`, `focusedCarIndex`, `track: TrackInfo?`, `cars: Map<Int, CarEntry>`, `entryListVersion`, `lastPreambleAt`, plus raw preamble bytes. Survives reconnects within a single `AccClient.connect` call. `isPreambleReady()` and `snapshotRawPreamble()` helpers.
- `ContextUpdater` — single owner of all mutations to `ClientContext`. Decodes `TRACK_DATA`, `ENTRY_LIST`, `ENTRY_LIST_CAR`. On `REGISTRATION_RESULT`, requests entry list + track data so the cache stays current after every reconnect. Detects track-name change and clears stale cars. Evicts cars not present in new `ENTRY_LIST` messages.
- `TrackInfo` — decoded snapshot of `TRACK_DATA` (name, id, meters, camera sets, HUD pages).
- `CarEntry` — decoded snapshot of `ENTRY_LIST_CAR` (carId, model, team, race number, cup category, drivers).
- `RawPreamble` — atomic byte snapshot for replay / recording consumers.
- `SessionEventListener` defers `onSessionStart` until preamble is ready (track + at least one car cached). Phase transitions that arrive before preamble do not fire start events with empty data.
- New documentation under `docs/`:
  - `SessionContextStrategy.md` — design spec for cross-session context caching.
  - `Recording.md`, `Listeners.md`, `ClientContext.md`, `Lifecycle.md` — user-facing reference.
  - `Architecture.md`, `WireProtocol.md`, `ListenerRecipes.md`, `Testing.md` — contributor reference.
  - `Migration.md` — migration guide for this release.
- New tests: `ContextUpdaterTest`, `SessionDetectorTest`. The latter uses real recorded UDP frames from the bundled playback CSV.

### Changed

- **Breaking:** `ClientContext.focusedCarIndex` is now `Int?` (was `Int`, default 0). `null` until the first `REALTIME_UPDATE` arrives. Distinguishes "no value yet" from a real focused carId of 0. Consumer code that read this field will need a null-check.
- **Breaking:** `SessionDetector` now takes `ClientContext` as its first constructor argument. Existing call sites must update from `SessionDetector(listOf(...))` to `SessionDetector(context, listOf(...))`.
- **Breaking:** `SessionPreamble` is now a decoded snapshot rather than raw `PreambleMessage` fields. Old fields (`trackData`, `entryList`, `carEntries: List<PreambleMessage>`) replaced by `track: TrackInfo`, `cars: Map<Int, CarEntry>`, `entryListVersion`, `capturedAt`, and `raw: RawPreamble`. See `docs/Migration.md` for translation patterns.
- **Behaviour:** First session of every reconnect now starts with full preamble, not empty preamble.
- **Behaviour:** Track changes mid-connection now clear `cars` and bump `entryListVersion`.
- Listener ordering now requires `ContextUpdater` to precede `SessionDetector`. Documented in `AccClient` KDoc; not enforced programmatically.
- `RecordingSessionListener` now reads bytes from `SessionPreamble.raw` and re-parses for the JSON column.

### Deprecated

- `ClientState` — replaced by `ClientContext` (kept as deprecated typealias).
- `RegistrationResultListener` — replaced by `ContextUpdater` (kept as deprecated thin wrapper).

### Migration notes

Most changes are source-compatible via deprecated aliases. The two breaking signatures are `SessionDetector` and `SessionPreamble`. See `docs/Migration.md` for step-by-step.

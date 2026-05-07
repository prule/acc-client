# Glossary

ACC sim-racing terms and library-specific terms in one place.

## ACC concepts

**ACC** — *Assetto Corsa Competizione*. Sim-racing game by Kunos Simulazioni. The library talks to its dedicated server.

**Broadcasting API** — UDP protocol exposed by ACC for external monitoring tools (live timing, telemetry overlays, race control). What this library connects to. Configured via `broadcasting.json`.

**broadcasting.json** — config file at `C:\Users\<user>\Documents\Assetto Corsa Competizione\Config\broadcasting.json`. Defines the UDP port, connection password, and command password the server will accept.

**Session** — one practice / qualifying / race / hotlap event in ACC. A connection sees zero or more sessions, separated by phase transitions through `POST_SESSION` / `SESSION_OVER`.

**Phase** — finer-grained state within a session: `STARTING`, `PRE_FORMATION`, `FORMATION_LAP`, `PRE_SESSION`, `SESSION`, `SESSION_OVER`, `POST_SESSION`, etc. Reported in every `REALTIME_UPDATE`. The library treats `FORMATION_LAP` + `SESSION` as session-active and `SESSION_OVER` + `POST_SESSION` as session-end.

**Session type** — what kind of session: `PRACTICE`, `QUALIFYING`, `SUPERPOLE`, `RACE`, `HOTLAP`, `HOTSTINT`, `HOTLAP_SUPERPOLE`, `REPLAY`, `NONE`. Reported in `REALTIME_UPDATE.sessionType`. Distinct from phase — a `RACE` session passes through many phases.

**Formation lap** — the lap before a race start where cars roll behind a pace car. ACC reports phase `FORMATION_LAP` during this. The library treats it as session-active so recording starts before the green flag.

**Hotlap / hotstint** — solo time-trial modes. Hotlap = single-lap attempts; hotstint = continuous laps with a fuel/tyre constraint.

**Cup category** — entry tier within an ACC race: `OVERALL_PRO`, `PRO_AM`, `AM`, `SILVER`, `NATIONAL`. Each car declares its category in `ENTRY_LIST_CAR`.

**Driver category** — driver skill tier (FIA-aligned for endurance racing): `BRONZE`, `SILVER`, `GOLD`, `PLATINUM`. Reported per driver in `ENTRY_LIST_CAR`.

**Driver swap** — during endurance races a car may have multiple registered drivers. `currentDriverIndex` on `EntryListCar` indicates who is in the seat right now. Updated mid-session via subsequent `ENTRY_LIST_CAR` packets.

**Splits** — the three sector times of a lap, in milliseconds. Reported in `RealtimeCarUpdate.bestSessionLap.splits` and `lastLap.splits`.

**Spline position** — float in `[0.0, 1.0]` indicating how far around the track centerline a car is. Reported in `RealtimeCarUpdate.splinePosition`.

**Track meters** — track length, in meters. Reported in `TRACK_DATA.trackMeters`. E.g. Red Bull Ring is 4318 m.

**Camera set / HUD page** — ACC's broadcast UI configurations. Names listed in `TRACK_DATA`. Used by command-mode tools to switch broadcast views; this client does not currently send those commands.

**Focused car** — the car ACC's broadcasting view is currently following. Index reported in `REALTIME_UPDATE.focusedCarIndex`. Useful for telemetry overlays that want to follow whatever the broadcaster is showing.

**REGISTER_COMMAND_APPLICATION** — name of the outbound packet that registers the client. Confusingly named — it registers any client (read-only or command-capable), not just command applications.

**connectionPassword vs commandPassword** — read access vs write/command access. Mismatch on `commandPassword` produces `isReadOnly=1` in the registration result; everything still streams in, but you can't send commands like changing the focused car.

**connectionId** — server-assigned integer identifying this client connection. New value on every reconnect. Required for outbound request packets (entry list, track data refresh, etc.).

## Library concepts

**Preamble** — the bundle of `TRACK_DATA` + `ENTRY_LIST` + `ENTRY_LIST_CAR` messages that arrive once per ACC connection but apply to every session within that connection. The library caches them in `ClientContext` so each session can be processed with full context.

**Preamble ready** — `ClientContext.isPreambleReady() == true`, meaning the cache has at least a track plus one car. `SessionDetector` uses this to decide whether to fire `onSessionStart` immediately or defer until the next preamble message arrives.

**Refresh-on-registration** — the strategy of re-requesting `ENTRY_LIST` and `TRACK_DATA` from the server on every `REGISTRATION_RESULT`. Implemented by `ContextUpdater`. Keeps the cache current after every reconnect without forcing the consumer to manage retransmits.

**Track change** — `TRACK_DATA` arrives with a different `trackName` than the cached one. Triggers eviction of all cached cars (since carIds are only meaningful within a track). Bumps `entryListVersion`.

**Entry list version** — monotonic counter on `ClientContext` that increments on track change AND on every `ENTRY_LIST`. Lets consumers detect roster turnover between snapshots.

**Listener ordering contract** — `ContextUpdater` MUST precede `SessionDetector` in `AccClient.connect`. Not enforced programmatically; documented in KDoc and `docs/Listeners.md`.

**Session preamble** (`SessionPreamble`) — immutable snapshot of `ClientContext` taken when a session starts. Includes decoded track + cars + raw bytes. Passed to `onSessionStart`.

**Recording** — writing UDP frames to a CSV file as they arrive, suitable for replay. Two recorders: `RecordingSessionListener` (one file per session, includes preamble) and `CsvWriterListener` (one file per connection).

**Replay / playback** — feeding a recorded CSV back through `AccSimulator` to drive a client without needing the real game. Not real-time; uses a fixed delay between messages.

**Source** — `ClasspathSource` or `FileSource`, abstraction over where the simulator reads its CSV from.

**MessageListener vs SessionEventListener** — broad-message-stream listener vs session-scoped lifecycle listener. The first sees every UDP packet; the second sees session start / end events plus per-message dispatch only while a session is active. See `docs/Listeners.md`.

**FilteredMessageListener** — decorator that wraps a `MessageListener<T>` and only forwards messages where the body class matches `T` and a predicate passes.

## Underlying protocol terms

**Kaitai Struct** — declarative binary parser format. ACC's broadcasting protocol is described as a `.ksy` file in the [acc-messages](https://github.com/prule/acc-messages) repo, which generates Java parser classes. This library treats those classes as the source of truth for parsing.

**Little-endian** — byte order used by the ACC broadcasting protocol. Multi-byte numerics have the least significant byte first. Important when reading raw hex from recordings or constructing test fixtures.

**Length-prefixed string** — `u2 length` (little-endian) followed by `length` bytes of UTF-8. ACC's wire format for all string fields. Exposed in Kaitai-generated classes as a wrapper object with `.length()` and `.data()` accessors.

**SocketTimeoutException** — thrown by `DatagramSocket.receive()` when no packet arrives within `soTimeout`. The library hardcodes this to 2000 ms in `MessageReceiver` — if ACC's update rate exceeds 2 seconds the receiver will spuriously disconnect and reconnect.

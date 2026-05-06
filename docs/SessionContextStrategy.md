# Session Context Strategy

## Problem

Client connects → registration → server sends `TRACK_DATA` + `ENTRY_LIST` + `ENTRY_LIST_CAR` (one per car) once. Server then streams `REALTIME_UPDATE` / `REALTIME_CAR_UPDATE` / `BROADCASTING_EVENT`. Sessions transition through phases (PRE_SESSION → PRACTICE → QUALIFYING → RACE → POST_SESSION etc.) without server re-sending track or entry list. Each session needs full track + car context.

Two extremes:
- **Disconnect/reconnect every session** → forces re-registration, server re-sends all preamble. Simple isolation, loses data during reconnect window, may miss session start.
- **Cache once, use forever** → no data loss, but stale if track/cars change mid-connection (server reconfig, cars join/leave).

## Decision: Persistent Cache + Refresh-on-Session-Start (hybrid)

Keep preamble cached in client-level context. On every session start, re-request preamble from server (cheap, idempotent). Server re-sends → cache updates → listeners get current snapshot. Detect track change → invalidate stale car entries.

Rationale:
- No reconnect downtime.
- Self-healing if cars added/removed between sessions.
- Single connection lifecycle = simpler resource handling.
- Reconnect logic stays orthogonal to session logic.

## Components

### `ClientContext` (replaces `ClientState`)

Single source of truth for cross-session data. Owned by `AccClient`, passed to listeners.

```
class ClientContext {
  var connectionId: Int = 0
  var focusedCarIndex: Int? = null
  var track: TrackInfo? = null
  val cars: MutableMap<Int, CarEntry> = ConcurrentHashMap()
  var entryListVersion: Long = 0  // bumped on ENTRY_LIST receipt
  var lastPreambleAt: Instant? = null
}
```

### `TrackInfo`

Snapshot of `TRACK_DATA` decoded into stable form (name, length, sectors, hudPages). Equality by track name → enables change detection.

### `CarEntry`

Snapshot of `ENTRY_LIST_CAR` decoded (carId, carModel, teamName, drivers, raceNumber, cupCategory, currentDriverIndex). Mutable fields update in place; new `carId` inserts; missing `carId` after fresh `ENTRY_LIST` → evict.

### `ContextUpdater` (new listener)

Replaces ad-hoc state mutation in `RegistrationResultListener` + caching in `SessionDetector`. Single listener owns mutations to `ClientContext`.

Handles:
- `REGISTRATION_RESULT` → set `connectionId`, request entry list + track data.
- `TRACK_DATA` → build `TrackInfo`. If name differs from current → clear `cars`, bump `entryListVersion`. Set `track`. Update `lastPreambleAt`.
- `ENTRY_LIST` → record expected `carId` set, bump `entryListVersion`. Cars not yet seen are added by subsequent `ENTRY_LIST_CAR` messages. Optionally evict cars not in the new list after a grace period.
- `ENTRY_LIST_CAR` → upsert into `cars`. Update `lastPreambleAt`.
- `REALTIME_UPDATE` → update `focusedCarIndex` only (no session lifecycle work).

### `SessionDetector` (refactored)

No longer caches messages itself. Reads from `ClientContext`. Detects phase transitions only.

On `REALTIME_UPDATE`:
- inactive → active phase: capture `SessionPreamble` from `ClientContext` snapshot, fire `onSessionStart(preamble)`. Optionally re-request entry list + track data here to refresh.
- active → end phase: fire `onSessionStop()`.

On any message during active session: forward `onSessionMessage(...)`.

On `onStop()` (socket closed): fire `onSessionStop()` if active. Do **not** clear `ClientContext` — survives reconnect.

### `SessionPreamble`

Becomes a snapshot, not raw messages:

```
data class SessionPreamble(
  val track: TrackInfo,
  val cars: List<CarEntry>,
  val entryListVersion: Long,
  val capturedAt: Instant,
)
```

Recording listeners that need raw bytes (e.g. `RecordingSessionListener` writes hex for replay) get them via a parallel `RawPreamble` kept by `ContextUpdater` — last-seen bytes per message type. This preserves byte-perfect replay without forcing all consumers to re-parse.

```
data class RawPreamble(
  val trackData: ByteArray?,
  val entryList: ByteArray?,
  val carEntries: Map<Int, ByteArray>,
)
```

`SessionPreamble` exposes `raw: RawPreamble` for listeners that need it.

## Lifecycle

```
connect()
  └─ socket open
     └─ register
        ├─ REGISTRATION_RESULT          → ContextUpdater sets connectionId, requests preamble
        ├─ TRACK_DATA                   → ContextUpdater builds TrackInfo (clears cars on track change)
        ├─ ENTRY_LIST + ENTRY_LIST_CAR* → ContextUpdater fills cars
        └─ REALTIME_UPDATE stream
           ├─ phase enters SESSION/FORMATION_LAP
           │   → SessionDetector snapshots ClientContext → onSessionStart(preamble)
           │   → optionally re-request entry list + track data
           ├─ data flows                  → onSessionMessage(...)
           └─ phase enters POST_SESSION/SESSION_OVER → onSessionStop()

  ↺ socket timeout / error → reconnect (AccClient loop)
     - ClientContext preserved
     - new connectionId on re-registration
     - server re-sends preamble (we re-request on REGISTRATION_RESULT)
     - cars/track refresh; track change clears car cache
```

## Edge Cases

| Scenario | Behavior |
|---|---|
| Practice → Qualifying same track | Cache hits, preamble snapshot fresh. Re-request optional. |
| Server changes track between sessions | New `TRACK_DATA` arrives, name differs, `cars` cleared, new `ENTRY_LIST` repopulates. |
| Car joins mid-session | `ENTRY_LIST_CAR` upserts into `cars`. `onSessionMessage` fires for it; consumers can look up via `ClientContext`. |
| Car leaves mid-session | Server typically resends `ENTRY_LIST` without that car. `entryListVersion` bumps. Consumers can diff. |
| Connection drop mid-session | `onSessionStop()` fires. Reconnect → re-register → preamble re-fetched → next phase transition starts new session. |
| `onSessionStart` fires before preamble fully arrives | Defer firing until `track != null && cars.isNotEmpty()`. If phase transitions before preamble ready, queue a one-shot pending start. |
| Stale cache after long idle | `lastPreambleAt` watchdog; if older than threshold, force re-request on session start. |

## Migration Steps

1. Create `TrackInfo`, `CarEntry`, `RawPreamble`, `ClientContext` (rename `ClientState`, keep deprecated alias).
2. Create `ContextUpdater : MessageListener<AccBroadcastingInbound>`. Move mutations from `RegistrationResultListener` into it. Keep `RegistrationResultListener` as thin wrapper or delete.
3. Refactor `SessionDetector` to read from `ClientContext` instead of caching. Update `SessionPreamble` to snapshot type with optional `raw`.
4. Update `RecordingSessionListener` to use `preamble.raw` for hex output.
5. Wire `ContextUpdater` first in listener list (must run before `SessionDetector`).
6. Tests: track-change invalidation, mid-session car upsert, reconnect preserves context, session-start defers until preamble ready.

## Listener Ordering Contract

`AccClient.connect(listeners)` ordering matters now:

```
listeners = [
  LoggingListener(),        // observability, no state
  ContextUpdater(context),  // MUST be before SessionDetector
  SessionDetector(context, [...]),
]
```

Document this in `AccClient` KDoc. Optional: enforce by making `AccClient` accept `(context, sessionListeners)` and assemble the list internally.

## Open Questions

- Does ACC server re-broadcast `TRACK_DATA` / `ENTRY_LIST` on request mid-connection, or only post-registration? If only post-registration → re-request on session start is no-op; cache-only path. Verify against `acc-messages` docs / live capture.
- Should `ClientContext` be thread-safe? `MessageReceiver` is single-threaded per socket, but listeners may read context from other threads. Use `ConcurrentHashMap` for `cars`, `@Volatile` for scalars.
- Should `onSessionStart` ever fire twice for same session if preamble arrives late? No — gate with `sessionActive` flag.

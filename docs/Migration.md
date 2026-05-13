# Migration Guide

For consumers upgrading past the session-handling refactor.

## Summary of breaking changes

| Old | New | Status |
|---|---|---|
| `ClientState` | `ClientContext` | Old name kept as deprecated typealias. |
| `focusedCarIndex: Int` (default 0) | `focusedCarIndex: Int?` (default null) | Type changed — null until first `REALTIME_UPDATE`. |
| `RegistrationResultListener(state)` | `ContextUpdater(context)` | Old class kept as deprecated wrapper. |
| `SessionListener` + `SessionState` | `ContextUpdater` + `ClientContext` (+ `SessionEventListener`s) | Old types still present but no longer wired by default. |
| `SessionPreamble(trackData, entryList, carEntries)` (raw `PreambleMessage` fields) | `SessionPreamble(track, cars, entryListVersion, capturedAt, raw)` (decoded snapshot + `RawPreamble`) | Constructor signature changed — call sites must be updated. |
| `SessionDetector(listeners)` | `SessionDetector(context, listeners)` | Constructor signature changed — must pass `ClientContext`. |
| Listener ordering — implicit | Listener ordering — `ContextUpdater` MUST precede `SessionDetector` | New contract. |

## Why the refactor

Track + car preamble data is sent once per ACC connection but is needed for every session within that connection. The old design had `SessionDetector` cache raw bytes itself, which:

- Couldn't survive reconnects (cache lived inside one `connect` call).
- Couldn't detect track changes.
- Couldn't expose decoded data to consumer listeners that ran alongside.

The new design moves the cache to `ClientContext` (caller-owned, survives reconnects), splits parsing into `ContextUpdater`, and makes `SessionDetector` a pure phase-transition detector that snapshots `ClientContext` on session start.

See [SessionContextStrategy.md](SessionContextStrategy.md) for full rationale.

## Step-by-step migration

### 1. Replace `ClientState` with `ClientContext`

```kotlin
// before
val state = ClientState()

// after
val context = ClientContext()
```

`ClientState` is a typealias to `ClientContext`, so the old name still compiles with a deprecation warning. Field names are identical for `connectionId` and `focusedCarIndex`. New fields available on `ClientContext`: `track`, `cars`, `entryListVersion`, `lastPreambleAt`, plus raw bytes accessors.

### 1b. Handle nullable `focusedCarIndex`

`focusedCarIndex` was `Int` (default 0); now `Int?` (default null). This distinguishes "no value yet" from carId 0, which is a valid car.

```kotlin
// before — silently used 0 if no REALTIME_UPDATE had arrived
val car = context.cars[context.focusedCarIndex]

// after — gate on null
val idx = context.focusedCarIndex ?: return
val car = context.cars[idx]
```

If your code did arithmetic / comparison with `focusedCarIndex`, you'll get compile errors on the call sites — fix by null-checking first or providing a fallback (`?: 0` if you genuinely want the old behavior).

### 2. Replace `RegistrationResultListener` with `ContextUpdater`

```kotlin
// before
RegistrationResultListener(state)

// after
ContextUpdater(context)
```

Behaviour: `ContextUpdater` does everything the old listener did (set `connectionId`, request entry list + track data, update `focusedCarIndex`) PLUS decodes track / cars and caches raw preamble bytes.

If you keep `RegistrationResultListener` it will still work — it now delegates to `ContextUpdater` internally. The deprecation warning is the only visible difference.

### 3. Update `SessionDetector` construction

```kotlin
// before
SessionDetector(
  listOf(myListener),
)

// after
SessionDetector(
  context,
  listOf(myListener),
)
```

The first argument is now `ClientContext` — the same instance you pass to `ContextUpdater`.

### 4. Listener ordering

```kotlin
// before — order didn't strictly matter for built-in listeners
listOf(
  LoggingListener(),
  RegistrationResultListener(state),
  SessionDetector(listOf(...)),
)

// after — ContextUpdater MUST precede SessionDetector
listOf(
  LoggingListener(),         // observers can sit anywhere
  ContextUpdater(context),   // populates ClientContext
  SessionDetector(           // reads ClientContext
    context,
    listOf(...),
  ),
)
```

If you reverse the order, `SessionDetector` snapshots stale (empty) preamble on the first session of every connection. The library does not enforce the order programmatically — it's a documented contract.

### 5. Update `SessionEventListener` consumers using `SessionPreamble`

The shape changed:

```kotlin
// before
data class SessionPreamble(
  val trackData: PreambleMessage?,         // raw bytes + parsed
  val entryList: PreambleMessage?,         // raw bytes + parsed
  val carEntries: List<PreambleMessage>,   // raw bytes + parsed
)

// after
data class SessionPreamble(
  val track: TrackInfo,                    // decoded snapshot
  val cars: Map<Int, CarEntry>,            // decoded snapshot
  val entryListVersion: Long,
  val capturedAt: Instant,
  val raw: RawPreamble,                    // raw bytes only
)

data class RawPreamble(
  val trackData: ByteArray?,
  val entryList: ByteArray?,
  val carEntries: Map<Int, ByteArray>,
)
```

#### Migration patterns

If you only used the **decoded** values (track name, car list):

```kotlin
// before
override fun onSessionStart(preamble: SessionPreamble) {
  val td = preamble.trackData?.message?.body() as? AccBroadcastingInbound.TrackData
  val name = td?.trackName()?.data()
  val carIds = preamble.carEntries.map {
    (it.message.body() as AccBroadcastingInbound.EntryListCar).carId()
  }
}

// after
override fun onSessionStart(preamble: SessionPreamble) {
  val name = preamble.track.name
  val carIds = preamble.cars.keys.toList()
}
```

If you needed **raw bytes** (e.g. for replay/recording):

```kotlin
// before
preamble.trackData?.bytes
preamble.entryList?.bytes
preamble.carEntries.map { it.bytes }

// after
preamble.raw.trackData
preamble.raw.entryList
preamble.raw.carEntries.values
```

If you needed **both** the parsed message and the bytes:

```kotlin
// before
preamble.trackData?.let { it.bytes to (it.message.body() as TrackData) }

// after — re-parse the bytes if you need the AccBroadcastingInbound wrapper:
preamble.raw.trackData?.let { bytes ->
  val parsed = AccBroadcastingInbound(
    ByteBufferKaitaiStream(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN))
  )
  bytes to parsed
}
// — or use the decoded TrackInfo:
preamble.raw.trackData to preamble.track
```

`PreambleMessage` (raw bytes + parsed message pair) still exists in [`SessionPreamble.kt`](../acc-client-core/src/main/kotlin/com/github/prule/acc/client/SessionPreamble.kt) for back-compat with consumers that want to use it directly, but it is no longer part of `SessionPreamble`.

### 6. `SessionListener` + `SessionState`

These are an older parallel design. The new `AccClient.main()` example does not use them, but they remain in the codebase. If you have a working consumer using `SessionListener(SessionState())`, it still works — but it does not benefit from the new cross-session preamble cache or session-detection logic.

Suggested migration path:

- Replace `SessionState` reads with `ClientContext` reads (richer data: `track: TrackInfo` instead of `track: String?`, `cars: Map<Int, CarEntry>` instead of `carMap: Map<Int, EntryListCar>`).
- Replace `SessionListener`'s message dispatch with either `ContextUpdater` (for cross-session data) or a custom `MessageListener` / `SessionEventListener` (for your domain logic).
- The "laps" and "lapsCompleted" fields on `SessionState` have no equivalent in `ClientContext` — those are domain logic; move them into your own listener.

## Behaviour changes you may notice

| Behaviour | Before | After |
|---|---|---|
| First session of every reconnect | `onSessionStart` fired with whatever `SessionDetector` happened to have cached (likely empty after socket drop) | `onSessionStart` fires with full preamble — `ClientContext` survives reconnects |
| Phase transitions to `SESSION` before preamble arrives | `onSessionStart` fired with empty preamble | `onSessionStart` deferred until preamble ready |
| Track changes mid-connection (e.g. server reconfig) | Old car list retained — stale | `cars` cleared; server re-sends entry list; fresh data |
| Mid-session car join / leave | Old car list silently outdated | `cars` upserted; `entryListVersion` bumped |

## Compile-time vs runtime breakage

| Change | Compile error? | Runtime risk if not migrated? |
|---|---|---|
| `ClientState` → `ClientContext` | No (typealias) | None |
| `RegistrationResultListener` → `ContextUpdater` | No (wrapper) | None — wrapper delegates |
| `SessionDetector(listeners)` → `SessionDetector(context, listeners)` | **Yes** | N/A |
| `SessionPreamble.trackData` etc. | **Yes** | N/A |
| Listener ordering | No | First session of every connection sees empty preamble |
| `SessionListener` / `SessionState` | No | None — old types still work, just don't benefit from new features |

If your code compiles after the upgrade, the only behaviour change to watch for is listener ordering. If your `SessionDetector` runs before `ContextUpdater`, swap them.

## See also

- [SessionContextStrategy.md](SessionContextStrategy.md) — design rationale.
- [Listeners.md](Listeners.md) — current listener contracts.
- [ClientContext.md](ClientContext.md) — full field reference for the new state object.

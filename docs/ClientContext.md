# ClientContext & Configuration

Reference for the cross-session state object and the connect-time configuration.

## ClientContext

[`ClientContext`](../src/main/kotlin/com/github/prule/acc/client/ClientContext.kt) is the shared state container holding everything cached across the lifetime of a single `AccClient.connect(...)` call — including across UDP reconnects.

You construct it once and pass it to both [`ContextUpdater`](../src/main/kotlin/com/github/prule/acc/client/ContextUpdater.kt) (which writes) and [`SessionDetector`](../src/main/kotlin/com/github/prule/acc/client/SessionDetector.kt) (which reads). Custom listeners that need track or car info should read from it as well.

```kotlin
val context = ClientContext()
AccClient(config).connect(
  listOf(
    LoggingListener(),
    ContextUpdater(context),
    SessionDetector(context, listOf(MyConsumerListener(context))),
  )
)
```

### Fields

| Field | Type | Set by | Meaning |
|---|---|---|---|
| `connectionId` | `Int` | `ContextUpdater` on `REGISTRATION_RESULT` | Server-assigned id. Changes on every reconnect. Used in outbound request packets. |
| `focusedCarIndex` | `Int?` | `ContextUpdater` on `REALTIME_UPDATE` | Index of the car currently focused by ACC's broadcasting view. `null` until the first `REALTIME_UPDATE` arrives — distinguishes "no value yet" from a real focused carId of 0. |
| `track` | `TrackInfo?` | `ContextUpdater` on `TRACK_DATA` | Decoded current track. `null` until first `TRACK_DATA` arrives. |
| `cars` | `MutableMap<Int, CarEntry>` | `ContextUpdater` on `ENTRY_LIST` / `ENTRY_LIST_CAR` | Decoded car entries by `carId`. Survives session boundaries; cleared on track change. |
| `entryListVersion` | `Long` | `ContextUpdater` | Monotonic counter — bumped on track change AND on every `ENTRY_LIST`. Use to detect roster changes. |
| `lastPreambleAt` | `Instant?` | `ContextUpdater` | Wall-clock time of the most recent preamble update. `null` until first preamble arrives. |
| `rawTrackData` | `ByteArray?` | `ContextUpdater` | Last-seen raw `TRACK_DATA` payload — for byte-perfect replay. |
| `rawEntryList` | `ByteArray?` | `ContextUpdater` | Last-seen raw `ENTRY_LIST` payload. |
| `rawCarEntries` | `MutableMap<Int, ByteArray>` | `ContextUpdater` | Last-seen raw `ENTRY_LIST_CAR` payload per car. |

### Helpers

| Method | Purpose |
|---|---|
| `isPreambleReady()` | `true` when both `track != null` and `cars` is non-empty. Used by `SessionDetector` to gate `onSessionStart`. |
| `snapshotRawPreamble()` | Atomic deep-copy of all raw bytes. Safe to retain across mutations. Used by `RecordingSessionListener` and `SessionPreamble.raw`. |

### Thread safety

| Field | Guarantee |
|---|---|
| Scalar fields (`connectionId`, `focusedCarIndex`, `track`, `entryListVersion`, `lastPreambleAt`, `rawTrackData`, `rawEntryList`) | `@Volatile` — visibility across threads. Writes are serialized by `MessageReceiver` (single coroutine). |
| `cars`, `rawCarEntries` | `ConcurrentHashMap` — safe for concurrent read + write. |
| Multi-field reads | **Not atomic.** Reading `track` and `cars` separately may catch a track change in progress. Use `snapshotRawPreamble()` or copy `cars.toMap()` if you need a consistent multi-field view. |

If you only ever read `ClientContext` from inside a `MessageListener.onMessage` (same coroutine as writes), you don't need to think about concurrency.

### Lifecycle

| Event | Effect |
|---|---|
| `AccClient.connect` starts | `ClientContext` exists empty. Caller passed it in. |
| Registration | `connectionId` set. `ContextUpdater` requests entry list + track data — server responds, populating `track` and `cars`. |
| Phase enters `SESSION` | `SessionDetector` snapshots into `SessionPreamble`. Context is unchanged. |
| Mid-session car update | `cars[carId]` upserted. Existing snapshots are unaffected. |
| Track change (different track name in new `TRACK_DATA`) | `cars` cleared, `rawCarEntries` cleared, `entryListVersion` bumped. Server then re-sends entry list + per-car entries. |
| `ENTRY_LIST` with reduced car set | Cars not in the new list are evicted. `entryListVersion` bumped. |
| Socket timeout / reconnect | All fields **preserved**. `connectionId` will be reassigned on the next registration. |
| `AccClient.stop()` | No effect on `ClientContext`. Caller is free to discard or reuse. |

### Reading from a custom listener

```kotlin
class FocusedCarTracker(private val context: ClientContext) : SessionEventListener {
  override fun onSessionMessage(
    bytes: ByteArray,
    message: AccBroadcastingInbound,
    sender: MessageSender,
  ) {
    val idx = context.focusedCarIndex ?: return
    val car = context.cars[idx] ?: return
    println("${car.raceNumber} ${car.teamName}")
  }
}
```

`focusedCarIndex` and `cars` are both updated by `ContextUpdater` before this listener runs — provided `ContextUpdater` precedes `SessionDetector` in the listener list.

## TrackInfo

[`TrackInfo`](../src/main/kotlin/com/github/prule/acc/client/TrackInfo.kt) is a stable decoded snapshot of `TRACK_DATA`.

| Field | Type | Notes |
|---|---|---|
| `name` | `String` | E.g. `"Red Bull Ring"`. Equality on this field drives track-change detection. |
| `id` | `Int` | ACC's internal track id. |
| `meters` | `Int` | Track length. |
| `cameraSets` | `List<CameraSet>` | Each with `name` + `cameras: List<String>`. |
| `hudPages` | `List<String>` | Available HUD page names. |

`cameraSets` and `hudPages` may be empty if the underlying acc-messages API differs from expected — those fields are decoded reflectively.

## CarEntry

[`CarEntry`](../src/main/kotlin/com/github/prule/acc/client/CarEntry.kt) is a stable decoded snapshot of `ENTRY_LIST_CAR`.

| Field | Type | Notes |
|---|---|---|
| `carId` | `Int` | Server-assigned. Used as map key in `ClientContext.cars`. |
| `carModelType` | `Int` | Lookup via [`CarModelRepository.findById()`](../src/main/kotlin/com/github/prule/acc/client/CarModelRepository.kt) for human-readable name. |
| `teamName` | `String` | E.g. `"Black Falcon"`. |
| `raceNumber` | `Int` | Car's race number. |
| `cupCategory` | `String` | Enum name string, e.g. `"OVERALL_PRO"`, `"SILVER"`. |
| `currentDriverIndex` | `Int` | Index into `drivers`. |
| `nationality` | `Int` | Country code. |
| `drivers` | `List<Driver>` | One per registered driver on the car. |

## RawPreamble

[`RawPreamble`](../src/main/kotlin/com/github/prule/acc/client/RawPreamble.kt) is an atomic snapshot of raw bytes from `ClientContext.snapshotRawPreamble()`.

| Field | Type | Notes |
|---|---|---|
| `trackData` | `ByteArray?` | Last-seen `TRACK_DATA` bytes. |
| `entryList` | `ByteArray?` | Last-seen `ENTRY_LIST` bytes. |
| `carEntries` | `Map<Int, ByteArray>` | Last-seen `ENTRY_LIST_CAR` bytes per `carId`. |

All byte arrays are independent copies — safe to retain.

## AccClientConfiguration

[`AccClientConfiguration`](../src/main/kotlin/com/github/prule/acc/client/AccClientConfiguration.kt) — passed to `AccClient` constructor. All fields are `val` (immutable).

| Field | Type | Default | Meaning |
|---|---|---|---|
| `name` | `String` | required | Client display name sent in the registration packet. Visible to the ACC server. |
| `port` | `Int` | `9000` | Server UDP port. Must match `updListenerPort` in ACC's `broadcasting.json`. |
| `updateMillis` | `Int` | `1000` | Requested update frequency for `REALTIME_UPDATE` / `REALTIME_CAR_UPDATE` messages. Sent in the registration packet — the server decides whether to honor it. Lower = more frequent updates = more bandwidth. |
| `connectionPassword` | `String` | `"asd"` | Must match `connectionPassword` in ACC's `broadcasting.json`. The same value is also sent as the command password slot — see caveat below. |
| `serverIp` | `String` | `"127.0.0.1"` | Server hostname or IP. Resolved via `InetAddress.getByName`. |
| `connectTimeout` | `Duration` | `10.seconds` | **Currently unused** by `AccClient` — reserved for future use. The hardcoded socket `soTimeout` in `AccClient.connect` is 2000 ms. |
| `retryPeriod` | `Duration` | `10.seconds` | **Currently unused** — reserved. The hardcoded reconnect delay in `AccClient.connect` is 1000 ms. |

### Where to find the values

ACC writes `broadcasting.json` to:

```
C:\Users\<username>\Documents\Assetto Corsa Competizione\Config\broadcasting.json
```

Example contents:

```json
{
  "updListenerPort": 9000,
  "connectionPassword": "asd",
  "commandPassword": ""
}
```

### Command password caveat

`AccClient.connect` sends `connectionPassword` for both the read connection password slot and the command password slot. If your server's `commandPassword` differs from `connectionPassword`, the registration handshake succeeds in **read-only mode** — you'll see `"Wrong command mode password, readonly access granted"` in the server's reply (the `errorMessage` field of `REGISTRATION_RESULT`). The client can still receive everything; it just can't issue commands.

This is fine for the current API surface (no command outbounds beyond the entry list / track data refresh, which work in read-only mode).

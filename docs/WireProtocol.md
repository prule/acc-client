# Wire Protocol Cheat Sheet

Reference for the ACC broadcasting UDP protocol as observed by this client.

> Authoritative parser: [acc-messages](https://github.com/prule/acc-messages) (Kaitai schema). This doc is a quick reference — when in doubt, read the `.ksy`.

## Transport

- **Protocol:** UDP, single port (default `9000`).
- **Byte order:** little-endian.
- **Encoding:** binary, length-prefixed strings (`u2 length` + UTF-8 bytes).
- **Framing:** none beyond the UDP packet boundary. One message per packet. First byte is the type discriminator.
- **Server config:** `C:\Users\<user>\Documents\Assetto Corsa Competizione\Config\broadcasting.json` — sets `updListenerPort`, `connectionPassword`, `commandPassword`.

## Inbound message types

`AccBroadcastingInbound.InboundMsgType` enum, also encoded as the first byte of the payload:

| Byte | Name | Body class | Purpose |
|---|---|---|---|
| 1 | `REGISTRATION_RESULT` | `RegistrationResult` | Server reply to register handshake. Carries `connectionId` + read-only flag + optional error. |
| 2 | `REALTIME_UPDATE` | `RealtimeUpdate` | Per-tick session state — phase, focused car, weather, best lap, current camera. |
| 3 | `REALTIME_CAR_UPDATE` | `RealtimeCarUpdate` | Per-tick per-car state — position, lap, splits, world coords, kmh, gear. |
| 4 | `ENTRY_LIST` | `EntryList` | Server-assigned `connectionId` + list of `carId`s currently in the entry list. |
| 5 | `TRACK_DATA` | `TrackData` | Track metadata — name, id, length in meters, camera sets, HUD pages. |
| 6 | `ENTRY_LIST_CAR` | `EntryListCar` | Per-car details — model, team, race number, cup category, drivers. One packet per car. |
| 7 | `BROADCASTING_EVENT` | `BroadcastingEvent` | Discrete event — lap completed, accident, penalty, etc. |

## Outbound message types

Built by `AccBroadcastingClient` (from acc-messages). The client sends these:

| Method | Sent when | Purpose |
|---|---|---|
| `buildRegisterCommandApplication(name, connectionPwd, updateMillis, commandPwd)` | `AccClient.connect()` opens a socket | Register the client with the server. |
| `buildRequestEntryList(connectionId)` | `ContextUpdater` on `REGISTRATION_RESULT` | Ask server to re-send `ENTRY_LIST` + `ENTRY_LIST_CAR`. |
| `buildRequestTrackData(connectionId)` | `ContextUpdater` on `REGISTRATION_RESULT` | Ask server to re-send `TRACK_DATA`. |

Other outbound methods exist in `AccBroadcastingClient` (focused-car-change, hud-page-change, instant-replay-request) but this client doesn't use them. They require a valid command password.

## Register handshake

```
client                                        server (ACC)
  │
  │── REGISTER (name, connPwd, updateMs, cmdPwd) ──►
  │
  │◄── REGISTRATION_RESULT(connectionId, success, isReadOnly, errorMsg)
  │
  │── REQUEST_ENTRY_LIST(connectionId) ─────────►
  │── REQUEST_TRACK_DATA(connectionId) ─────────►
  │
  │◄── TRACK_DATA(...)
  │◄── ENTRY_LIST(carIndexes[])
  │◄── ENTRY_LIST_CAR(carId=0, ...)
  │◄── ENTRY_LIST_CAR(carId=1, ...)
  │◄── ...
  │◄── REALTIME_UPDATE(phase, focusedCarIndex, ...)
  │◄── REALTIME_CAR_UPDATE(carIndex=0, ...)
  │◄── REALTIME_CAR_UPDATE(carIndex=1, ...)
  │◄── ...                                     ← stream continues
```

Notes:
- The server sends `REGISTRATION_RESULT` even on partial failure (e.g. wrong command password). `isReadOnly = 1` and `errorMessage` are how it tells you.
- `TRACK_DATA` and `ENTRY_LIST` are not sent automatically after register — `ContextUpdater` requests them. If you build a custom client without `ContextUpdater`, you must request them yourself.
- `ENTRY_LIST_CAR` packets arrive one per car, in arbitrary order. The full set may interleave with `REALTIME_*` packets.

## Read-only mode

If `commandPassword` doesn't match, the server replies with:

```
REGISTRATION_RESULT {
  connectionId: <some int>,
  connectionSuccess: 1,
  isReadOnly: 1,
  errorMessage: "Wrong command mode password, readonly access granted"
}
```

You can still receive everything; you just can't issue command outbounds (focused-car-change, hud-page-change, instant-replay). The two outbounds this client uses (`requestEntryList`, `requestTrackData`) work in read-only mode.

`AccClient.connect` currently sends `connectionPassword` as both passwords, so unless your `commandPassword` is also set to that value, expect read-only.

## Phase enum

`REALTIME_UPDATE.phase` (string in JSON, enum at the type level):

| Value | Meaning |
|---|---|
| `NONE` | No active session (lobby, between sessions). |
| `STARTING` | Session loading. |
| `PRE_FORMATION` | Pre-formation lap (race only). |
| `FORMATION_LAP` | Formation lap in progress. `SessionDetector` treats as session-active. |
| `PRE_SESSION` | Just before green flag / session start. |
| `SESSION` | Active session (practice, qualifying, race). `SessionDetector` treats as session-active. |
| `SESSION_OVER` | Session finished. `SessionDetector` fires `onSessionStop`. |
| `POST_SESSION` | After-session screen. `SessionDetector` fires `onSessionStop`. |
| `RESULT_UI` | Results screen. |

The `SessionDetector` only inspects two sets:

- Active: `{FORMATION_LAP, SESSION}`
- End: `{SESSION_OVER, POST_SESSION}`

Other phases pass through without triggering session lifecycle events.

## Session type

`REALTIME_UPDATE.sessionType` (string):

| Value | Meaning |
|---|---|
| `PRACTICE` | Practice session. |
| `QUALIFYING` | Qualifying. |
| `SUPERPOLE` | Superpole. |
| `RACE` | Race. |
| `HOTLAP` | Hotlap. |
| `HOTSTINT` | Hotstint. |
| `HOTLAP_SUPERPOLE` | Hotlap superpole. |
| `REPLAY` | Replay playback. |
| `NONE` | Between sessions. |

The client doesn't act on this directly — it's available to consumer listeners via the parsed body.

## Cup category enum

`ENTRY_LIST_CAR.cupCategory`:

| Value |
|---|
| `OVERALL_PRO` |
| `PRO_AM` |
| `AM` |
| `SILVER` |
| `NATIONAL` |

## Broadcasting event types

`BROADCASTING_EVENT.type` byte:

| Byte | Name |
|---|---|
| 0 | `NONE` |
| 1 | `GREEN_FLAG` |
| 2 | `SESSION_OVER` |
| 3 | `PENALTY_COMM_MSG` |
| 4 | `ACCIDENT` |
| 5 | `LAP_COMPLETED` |
| 6 | `BEST_SESSION_LAP` |
| 7 | `BEST_PERSONAL_LAP` |

The body also carries `msg` (string), `timeMs` (int), `carId` (int).

## String encoding

Strings on the wire: `u2 length` (little-endian) + `length` bytes UTF-8.

Kaitai exposes them as a wrapper object — at the JVM level, `wrapper.data()` returns the `String`, `wrapper.length()` returns the byte count. Empty strings are `length=0` with no following bytes.

## Numeric types

| Kaitai type | JVM | Notes |
|---|---|---|
| `u1` | `int` (sign-extended-safe via `Byte.toInt() and 0xFF`) | Single byte, 0–255. |
| `u2le` | `int` | Two bytes, little-endian, 0–65535. |
| `u4le` | `long` | Four bytes, little-endian, 0–~4.3B. |
| `s4le` | `int` | Signed four bytes, little-endian. |
| `f4le` | `float` | IEEE-754 single, little-endian. |

`ContextUpdater.numeric()` coerces all numeric returns to `Int` via `(value as Number).toInt()` — fine for fields that fit, lossy for genuine `u4` values that overflow.

## Update rate

`REALTIME_UPDATE` and `REALTIME_CAR_UPDATE` arrive at roughly the rate set by the client's `updateMillis` in the register packet. Defaults to 1000 ms in `AccClientConfiguration`. The server may not honor exactly — observed jitter ±100 ms.

If `updateMillis > 2000`, expect spurious socket timeouts in `MessageReceiver` (its `soTimeout` is hardcoded to 2000 ms). Keep it under 2 seconds.

`ENTRY_LIST_CAR` packets are bursty around session start / driver swaps but otherwise rare. `BROADCASTING_EVENT` is event-driven (laps, accidents, penalties).

## Parsing

```kotlin
import io.kaitai.struct.ByteBufferKaitaiStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

val bytes: ByteArray = ...                               // from socket.receive()
val stream = ByteBufferKaitaiStream(
  ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
)
val message = AccBroadcastingInbound(stream)             // parses immediately

when (message.msgType()) {
  AccBroadcastingInbound.InboundMsgType.TRACK_DATA -> {
    val td = message.body() as AccBroadcastingInbound.TrackData
    val name = td.trackName().data()
    ...
  }
  ...
}
```

`message.body()` returns a `KaitaiStruct` polymorphically — cast to the type matching `msgType()`.

## See also

- [acc-messages repo](https://github.com/prule/acc-messages) — Kaitai schema, regenerate sources.
- [docs/UDP.md](UDP.md) — generic Java UDP request/reply pattern.
- [docs/Lifecycle.md](Lifecycle.md) — how this client drives the protocol.

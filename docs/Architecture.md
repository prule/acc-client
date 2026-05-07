# Architecture Overview

Reference for contributors. Module map, layering, data flow, dependency graph.

## Module map

```
com.github.prule.acc.client/
├── AccClient                       Connection lifecycle (socket, register, reconnect loop)
├── AccClientConfiguration          Connect-time settings
├── MessageReceiver                 Receive loop — bytes → parsed message → listener dispatch
├── MessageListener<T>              Base listener interface (onStart / onMessage / onStop)
├── FilteredMessageListener         MessageListener decorator with type + predicate filter
├── LoggingListener                 Observer — debug-logs every message
├── JsonFormatter                   Jackson + Kaitai mix-in for serializing messages to JSON
│
├── ContextUpdater                  Owns mutations to ClientContext from preamble messages
├── ClientContext                   Cross-session shared state — track, cars, raw bytes, ids
├── ClientState                     Deprecated typealias for ClientContext
├── TrackInfo                       Decoded TRACK_DATA snapshot
├── CarEntry                        Decoded ENTRY_LIST_CAR snapshot
├── RawPreamble                     Atomic byte snapshot for replay
│
├── SessionDetector                 Phase-transition detector — fires SessionEventListeners
├── SessionEventListener            Session-scoped listener interface
├── SessionPreamble                 Snapshot of ClientContext at session start
│
├── RecordingSessionListener        Per-session CSV recorder
├── CsvWriterListener               Per-connection CSV recorder
├── EventRow                        CSV row data class
│
├── RegistrationResultListener      Deprecated thin wrapper around ContextUpdater
├── SessionListener                 Legacy listener (parallel design — not used in main wiring)
├── SessionState                    Legacy state (parallel design — not used in main wiring)
│
├── CarModelRepository              Lookup table: carModelType (Int) → CarModel(name)
├── ProgressReporter                Utility — log progress every N items
│
└── simulator/
    ├── AccSimulator                Pretends to be ACC; replays a CSV to a connected client
    ├── AccSimulatorConfiguration   Simulator settings
    ├── EventPlayer                 CSV-driven sender — fixed-delay playback
    ├── PlaybackEventsRepository    Loads CSV → List<EventRow>
    ├── RegisterListener            Awaits client's register packet, then triggers EventPlayer
    └── Source / ClasspathSource / FileSource   Abstraction over CSV input
```

## Layering

Three logical layers, bottom-up:

1. **Transport** — `AccClient`, `MessageReceiver`, `MessageSender`. UDP socket, raw bytes, parse via `AccBroadcastingInbound`.
2. **State** — `ContextUpdater`, `ClientContext` (+ `TrackInfo`, `CarEntry`, `RawPreamble`). Decode preamble messages into stable domain objects, cache across sessions and reconnects.
3. **Domain events** — `SessionDetector`, `SessionEventListener`, `SessionPreamble`. Translate the message stream into session lifecycle events for consumers.

Recording, logging, and the simulator are orthogonal — they sit alongside these layers, not above them.

## Data flow

```
ACC server                                       acc-client
    │
    │ UDP packet
    ├────────────────────────────────────────►   DatagramSocket
    │                                                │
    │                                                ▼
    │                                            MessageReceiver
    │                                                │ ByteArray + ByteBufferKaitaiStream
    │                                                ▼
    │                                            AccBroadcastingInbound  ◄── from acc-messages dep
    │                                                │
    │                                                ▼
    │                                            for each listener (in order):
    │                                                onMessage(bytes, parsed, sender)
    │                                                ├── LoggingListener        → log
    │                                                ├── ContextUpdater         → mutate ClientContext
    │                                                │                             ├── decode TrackInfo / CarEntry
    │                                                │                             └── snapshot raw bytes
    │                                                └── SessionDetector        → read ClientContext
    │                                                                              ├── snapshot SessionPreamble
    │                                                                              └── dispatch to SessionEventListeners
    │                                                                                  ├── RecordingSessionListener → CSV
    │                                                                                  └── (your listeners)
    ▼
```

Outbound (rare):
- `ContextUpdater` calls `messageSender.send(...)` after `REGISTRATION_RESULT` to request entry list + track data refresh. Same socket, same coroutine.

## Listener dispatch

`MessageReceiver.start()` runs a `while(true)` loop on a single coroutine. Per packet:

1. Block on `socket.receive()`.
2. Copy bytes, wrap in little-endian `ByteBufferKaitaiStream`, construct `AccBroadcastingInbound`.
3. Build `MessageSender` from `packet.socketAddress`.
4. Call `listener.onMessage(...)` for each listener in registration order, **synchronously**.

No threading, no buffering, no backpressure. A slow listener directly slows the receive loop.

## Dependency graph

```
AccClient depends on:
  ├── AccBroadcastingClient    (acc-messages — outbound packet builder)
  ├── AccBroadcastingInbound   (acc-messages — Kaitai-generated parser)
  ├── MessageReceiver
  └── kotlinx-coroutines-core

MessageReceiver depends on:
  ├── DatagramSocket (java.net)
  ├── ByteBufferKaitaiStream (kaitai-struct-runtime)
  └── MessageListener

ContextUpdater depends on:
  ├── ClientContext
  ├── AccBroadcastingClient    (for buildRequestEntryList / buildRequestTrackData)
  ├── AccBroadcastingInbound   (for type discrimination + body cast)
  ├── TrackInfo / CarEntry
  └── slf4j

SessionDetector depends on:
  ├── ClientContext (read-only)
  ├── SessionEventListener
  └── SessionPreamble

RecordingSessionListener depends on:
  ├── SessionEventListener
  ├── SessionPreamble
  ├── JsonFormatter
  └── kotlin-csv

simulator/* depends on:
  ├── AccBroadcastingOutbound  (acc-messages — receive side)
  ├── MessageReceiver          (re-uses client's loop for the server side)
  └── kotlin-csv
```

External deps from `build.gradle.kts`:

| Dep | Used for |
|---|---|
| `com.github.prule:acc-messages` | Kaitai-generated message classes (inbound + outbound). Public API surface. |
| `kaitai-struct-runtime` | (transitive via acc-messages) Byte stream parser. |
| `kotlinx-coroutines-core` | `Dispatchers.IO`, `withContext`, `launch`, `delay`. |
| `kotlinx-serialization-json` | Used by `EventRow` parsing in playback CSV. |
| `jackson-databind` | `JsonFormatter` — serializes Kaitai structs with field-level visibility. |
| `kotlin-csv-jvm` | CSV read/write for recording + playback. |
| `kotlin-grass-*` | CSV → data class binding (`CarModelRepository`). |
| `logback-classic` | Default slf4j backend. |
| `mockito-kotlin`, `mockk`, `assertj`, `junit` | Tests only. |

`acc-client` exposes `acc-messages` via `api(...)` — consumers see the message types directly.

## Package boundaries

Currently a single flat package: `com.github.prule.acc.client`. The `simulator` subpackage is the only nested one.

Implication: there's no internal/public distinction at the package level. Anything exported by the JAR is consumable. If you make breaking changes to `MessageReceiver`, `ContextUpdater`, or the listener interfaces, downstream code may need to adapt.

## Concurrency model

- One coroutine per active connection (in `MessageReceiver.start()`).
- All listener callbacks run on that coroutine — no per-listener locking needed for state read/written only inside `onMessage`.
- `ClientContext` uses `@Volatile` + `ConcurrentHashMap` so external reader threads see consistent values per field.
- `EventPlayer` (simulator) launches on `GlobalScope` — outbound playback runs on its own coroutine, separate from the receive loop.

## Extension points

Adding behavior:

| Goal | Add a... |
|---|---|
| React to a specific message type | `MessageListener<AccBroadcastingInbound>` (or wrap in `FilteredMessageListener`) |
| React to session boundaries | `SessionEventListener`, register with `SessionDetector` |
| Cache more cross-session state | Extend `ClientContext`, mutate from `ContextUpdater` |
| Persist messages somewhere | `MessageListener` writing to your sink |
| Reply to the server | `messageSender.send(bytes)` from inside `onMessage` |

Avoid:
- Mutating `ClientContext` from anywhere other than `ContextUpdater` — race conditions and ordering bugs.
- Long-blocking work in `onMessage` — stalls the receive loop. Hand off to your own coroutine if needed.

## Legacy code

`SessionListener` and `SessionState` are an older parallel design predating `ContextUpdater` + `ClientContext` + `SessionDetector`. They are not used in the main `AccClient.main()` wiring but remain in the codebase for backward compatibility with any external consumer that imported them. Prefer the new types.

`RegistrationResultListener` is now a deprecated thin wrapper around `ContextUpdater`. Same story.

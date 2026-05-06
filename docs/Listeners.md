# Listeners

Reference for the three listener interfaces, their lifecycle hooks, ordering rules, and error semantics.

## Interface map

| Interface | Granularity | When fired | Owner |
|---|---|---|---|
| [`MessageListener<T>`](../src/main/kotlin/com/github/prule/acc/client/MessageListener.kt) | Every inbound UDP message | Connection-scoped — `onStart` → `onMessage`* → `onStop` | `MessageReceiver` (one instance per `AccClient.connect` call) |
| [`SessionEventListener`](../src/main/kotlin/com/github/prule/acc/client/SessionEventListener.kt) | Session boundaries + in-session messages | Session-scoped — `onSessionStart` → `onSessionMessage`* → `onSessionStop` | `SessionDetector` |
| [`FilteredMessageListener<T>`](../src/main/kotlin/com/github/prule/acc/client/FilteredMessageListener.kt) | Subset of inbound messages by type + predicate | Wraps `MessageListener` — only forwards matching messages | User wiring |

`SessionEventListener`s are not registered directly with `AccClient.connect`; they are registered with a `SessionDetector` (which is itself a `MessageListener<AccBroadcastingInbound>`).

## MessageListener

```kotlin
interface MessageListener<T> {
  fun onStart() {}
  fun onMessage(bytes: ByteArray, message: T, messageSender: MessageSender)
  fun onStop() {}
}
```

| Hook | Fires |
|---|---|
| `onStart` | Once, when the receive loop starts (after the socket opens). |
| `onMessage` | Per UDP packet received. `bytes` is the raw payload, `message` is the parsed `AccBroadcastingInbound`, `messageSender` lets you reply on the same socket. |
| `onStop` | Once, when the receive loop ends (socket timeout, exception, or `AccClient.stop()`). Always called via `try/finally` — guaranteed even on exception. |

Reconnect note: `AccClient` re-runs the receive loop in its `connect` while-loop, so the same listener instance receives a fresh `onStart` → `onStop` cycle per reconnect attempt.

## SessionEventListener

```kotlin
interface SessionEventListener {
  fun onSessionStart(preamble: SessionPreamble) {}
  fun onSessionMessage(bytes: ByteArray, message: AccBroadcastingInbound, sender: MessageSender) {}
  fun onSessionStop() {}
}
```

| Hook | Fires |
|---|---|
| `onSessionStart` | When `REALTIME_UPDATE.phase` enters `FORMATION_LAP` or `SESSION` AND preamble (track + at least one car) is cached. If the phase enters early without preamble, the start is **deferred** until the next preamble message arrives. The supplied [`SessionPreamble`](../src/main/kotlin/com/github/prule/acc/client/SessionPreamble.kt) is an immutable snapshot of `ClientContext` at that moment. |
| `onSessionMessage` | Per inbound message, **only while a session is active**. Fires for the `REALTIME_UPDATE` that triggered the session start. Does not fire for preamble messages received before the session started. |
| `onSessionStop` | When `REALTIME_UPDATE.phase` enters `SESSION_OVER` or `POST_SESSION`, OR when the underlying receive loop stops mid-session (socket timeout, etc.). Idempotent — only fires if a session was active. |

See [SessionContextStrategy.md](SessionContextStrategy.md) for the cache + defer semantics.

## FilteredMessageListener

```kotlin
FilteredMessageListener<T : Any>(
  clazz: KClass<T>,
  filter: (T) -> Boolean = { true },
  listeners: List<MessageListener<T>>,
)
```

A `MessageListener<AccBroadcastingInbound>` that:

1. Inspects each inbound message.
2. Picks either the wrapper (`AccBroadcastingInbound`) or the body (`message.body()`) — whichever matches `clazz`.
3. Applies `filter`.
4. Forwards matching messages to the inner listeners.

Use when downstream listeners want a typed body and don't care about non-matching messages.

```kotlin
FilteredMessageListener<AccBroadcastingInbound.RealtimeCarUpdate>(
  filter = { it.carIndex() == 13 },
  listeners = listOf(myCarTracker),
)
```

The `inline reified` companion form is recommended over the `KClass` constructor.

## Listener ordering

Listeners run sequentially in the order passed to `AccClient.connect(...)`. Order matters when one listener's effect is required by another.

### Required order

```
LoggingListener (or any pure observer)
ContextUpdater(context)         // populates ClientContext
SessionDetector(context, [...]) // snapshots ClientContext on session start
```

`ContextUpdater` MUST precede `SessionDetector`. If swapped, `SessionDetector` snapshots stale data and the first session of every connection starts with an empty preamble.

### Free ordering

`LoggingListener`, `CsvWriterListener`, and any custom observer-only listeners can sit anywhere — they don't depend on each other. Convention: put observers first so they capture every message before any state mutation.

## Error semantics

- An exception thrown from `onMessage` propagates to `MessageReceiver`, which logs it and **breaks the receive loop**. Subsequent messages are not delivered to any listener; `onStop` then fires. The outer `AccClient` reconnect loop then tries again.
- Therefore: treat `onMessage` as best-effort. Wrap risky work (file IO, network calls, parsing) in `try/catch` if you don't want one bad message to kill the connection.
- `onStart` and `onStop` exceptions are **not** caught by `MessageReceiver`. A throwing `onStart` will crash the receive loop; a throwing `onStop` will mask later listeners' `onStop`.

## Threading

- `MessageReceiver` runs on a single coroutine (Dispatchers.IO). All `onMessage` calls for a given connection are serialized — no listener-internal locking needed for state read/written only inside `onMessage`.
- If you expose listener state to other threads, that state needs its own synchronization. `ClientContext` already does this (`@Volatile` + `ConcurrentHashMap`).
- `MessageSender.send` writes to the same socket as the receiver. Calls are not synchronized by the framework — concurrent sends from outside the receive coroutine are not safe.

## Custom listener recipes

### Observe one message type

```kotlin
class TrackChangeLogger : MessageListener<AccBroadcastingInbound> {
  override fun onMessage(bytes: ByteArray, message: AccBroadcastingInbound, sender: MessageSender) {
    if (message.msgType() == AccBroadcastingInbound.InboundMsgType.TRACK_DATA) {
      val td = message.body() as AccBroadcastingInbound.TrackData
      println("Track: ${td.trackName().data()}")
    }
  }
}
```

### React to session boundaries

```kotlin
class SessionFileNamer : SessionEventListener {
  private var name: String? = null
  override fun onSessionStart(preamble: SessionPreamble) {
    name = "${preamble.track.name}-${preamble.capturedAt}".replace(" ", "_")
    println("Session started: $name")
  }
  override fun onSessionStop() {
    println("Session ended: $name")
    name = null
  }
}
```

### Reply to the server

`messageSender.send(bytes)` writes back to the sender's address on the same socket. Used internally by `ContextUpdater` to request entry list / track data after registration. Custom listeners should rarely need this — the server already pushes everything on its own schedule.

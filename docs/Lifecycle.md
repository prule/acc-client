# Connection Lifecycle

Reference for what happens between `AccClient(config).connect(listeners)` and the call returning. Covers the register handshake, message delivery, reconnect-on-timeout, and `stop()` semantics.

## High-level flow

```
connect(listeners)
└─ while running:
   ├─ open DatagramSocket (ephemeral local port, soTimeout = 2000ms)
   │  ├─ launch coroutine: MessageReceiver.start()
   │  │   ├─ listeners.onStart()
   │  │   ├─ loop: socket.receive() → parse → listeners.onMessage()
   │  │   └─ on SocketTimeoutException OR Exception:
   │  │      └─ listeners.onStop()  (try/finally — always fires)
   │  ├─ delay 1000ms
   │  ├─ send register packet (name, password, updateMillis)
   │  └─ await receiver coroutine completion
   ├─ socket closes (use{} block)
   └─ if running: delay 1000ms, loop again
```

## Phases

### 1. Socket setup

Each iteration of the connect loop opens a fresh `DatagramSocket` on an OS-assigned local port. `soTimeout` is **hardcoded to 2000ms**. If no UDP packet arrives within 2 seconds, `socket.receive()` throws `SocketTimeoutException`, which terminates the receive loop.

Implication: a healthy ACC server must send at least one packet every 2 seconds. The server's heartbeat (`REALTIME_UPDATE` driven by `updateMillis`) normally satisfies this. If `updateMillis` is set to `> 2000`, expect spurious disconnects.

### 2. Receiver coroutine

The `MessageReceiver` runs on `Dispatchers.IO`. It calls `onStart` on every listener, then loops:

1. `socket.receive(packet)` — blocks until UDP arrives or timeout.
2. Copy `packet.length` bytes to a fresh `ByteArray`.
3. Wrap as `ByteBufferKaitaiStream` (little-endian) and parse via `AccBroadcastingInbound`.
4. Construct `MessageSender` from the packet's source address.
5. Dispatch to each listener's `onMessage(bytes, message, sender)` in registration order.

Listeners run **synchronously and serially** in this coroutine. A slow listener delays delivery to subsequent listeners and the next `socket.receive()`.

### 3. Register

After waiting 1 second (giving the receiver coroutine time to start), `AccClient` sends the register packet built by `AccBroadcastingClient.buildRegisterCommandApplication(name, password, updateMillis, password)`.

The server replies with a `REGISTRATION_RESULT` containing the assigned `connectionId` (and an `errorMessage` if anything went wrong — most commonly the read-only warning, see [ClientContext.md](ClientContext.md#command-password-caveat)).

`ContextUpdater` stores the `connectionId` and immediately requests the entry list and track data. The server replies with a `TRACK_DATA` packet, an `ENTRY_LIST` packet, and one `ENTRY_LIST_CAR` per car.

### 4. Steady state

The server then streams:

- `REALTIME_UPDATE` at roughly `updateMillis` intervals — session phase, focused car, weather, best lap.
- `REALTIME_CAR_UPDATE` per active car per update tick — position, lap, splits, location.
- `BROADCASTING_EVENT` ad-hoc — lap completed, penalty issued, accident.
- Occasional `TRACK_DATA` / `ENTRY_LIST` / `ENTRY_LIST_CAR` if anything in the preamble changed.

`SessionDetector` watches phase transitions in `REALTIME_UPDATE` and fires `onSessionStart` / `onSessionStop` to its registered `SessionEventListener`s.

### 5. Disconnect

Three ways the receive loop ends:

| Trigger | Path |
|---|---|
| Socket timeout (no packet in 2s) | `SocketTimeoutException` → caught → loop breaks → `onStop` fires. |
| Other receive/parse exception | Logged at `error` → caught → loop breaks → `onStop` fires. |
| `AccClient.stop()` | Sets `running = false`. The current receive call still blocks until its 2-second timeout, then the loop sees `running = false` and exits without reconnecting. |

After `onStop`, control returns to `AccClient.connect`. The socket is closed by the `use{}` block.

### 6. Reconnect

If `running` is still true (i.e. the disconnect was not caused by `stop()`):

1. Wait 1000ms.
2. Open a fresh socket.
3. Repeat from phase 1.

A new `connectionId` is assigned by the server on re-registration. `ClientContext` is **not** reset — track and cars survive. `ContextUpdater` immediately requests fresh entry list + track data, so any changes that happened during the gap are caught up.

`SessionDetector`'s `sessionActive` flag IS reset (via `onStop` firing `onSessionStop` on its listeners during the disconnect). The next phase transition into `SESSION` / `FORMATION_LAP` starts a new session. If you were recording, this means a new file opens.

## Lifecycle hooks summary

| Event | `MessageListener` | `SessionEventListener` |
|---|---|---|
| Connect loop starts | — | — |
| Socket opens, receiver launches | `onStart` | — |
| Register sent + reply received | `onMessage` (REGISTRATION_RESULT) | — |
| Preamble arrives | `onMessage` × N | — |
| Phase enters `SESSION` (preamble ready) | `onMessage` | `onSessionStart` |
| Phase enters `SESSION` (preamble not ready) | `onMessage` | (deferred until preamble arrives) |
| Steady-state messages | `onMessage` | `onSessionMessage` |
| Phase enters `POST_SESSION` / `SESSION_OVER` | `onMessage` | `onSessionStop` |
| Socket times out / errors | `onStop` | `onSessionStop` (if active) |
| Reconnect: socket reopens | `onStart` (again) | — |
| `AccClient.stop()` called | `onStop` (after current timeout fires) | `onSessionStop` (if active) |

## What survives across reconnects

| State | Survives? | Notes |
|---|---|---|
| `ClientContext` (track, cars, raw bytes) | Yes | The instance is owned by the caller, not by the connection. |
| `connectionId` | Reassigned | New value on re-registration; `ContextUpdater` overwrites. |
| `SessionDetector.sessionActive` | Reset to `false` | Triggers `onSessionStop` if active. |
| `RecordingSessionListener`'s open file | Closed | New file opens on the next session start. |
| Listener `onStart` state | Reset per connection | `onStart` fires again after reconnect. |

## Stopping cleanly

`AccClient.stop()` sets a `running` flag — it does not interrupt the blocking `socket.receive()` call. Worst-case latency before the connect loop exits is the `soTimeout` of 2000ms.

If you need a faster stop, the current implementation does not support it — you'd have to close the socket from outside, which is not exposed.

```kotlin
val client = AccClient(config)
val job = launch { client.connect(listeners) }

delay(60.seconds)
client.stop()
job.join()  // waits up to ~2s for the receive loop to notice
```

## Concurrency model summary

- Single coroutine on `Dispatchers.IO` per connection.
- All listener callbacks (`onStart`, `onMessage`, `onStop`) run on that one coroutine.
- `MessageSender.send` writes to the same socket — safe from inside `onMessage`.
- External threads reading `ClientContext` see consistent values for individual fields (`@Volatile`, `ConcurrentHashMap`) but should snapshot if they need a multi-field consistent view.

## Known limitations

| Limitation | Impact |
|---|---|
| `connectTimeout` and `retryPeriod` config fields are unused | Hardcoded 2000ms socket timeout, 1000ms reconnect delay. |
| `stop()` waits up to one socket timeout | No way to force-close mid-receive without modifying `AccClient`. |
| No backpressure on listeners | A slow listener stalls the entire receive loop. |
| Reconnect is unconditional while `running` | No max-retries or backoff; will hammer indefinitely if the server is down. |
| Errors in `onStart` / `onStop` not caught | A crashing listener can bring down the receive loop or mask other listeners' shutdown. |

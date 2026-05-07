# Custom Listener Recipes

Working examples for common listener patterns. All snippets compile against the public API.

> Background reading: [Listeners.md](Listeners.md) for the interface contracts, [WireProtocol.md](WireProtocol.md) for message types, [ClientContext.md](ClientContext.md) for shared state.

## Recipe 1 — Track-change notifier

Log every time the track changes. Demonstrates `MessageListener` + type discrimination.

```kotlin
package myapp

import com.github.prule.acc.client.MessageListener
import com.github.prule.acc.client.MessageSender
import com.github.prule.acc.messages.AccBroadcastingInbound

class TrackChangeNotifier : MessageListener<AccBroadcastingInbound> {
  private var lastTrack: String? = null

  override fun onMessage(
    bytes: ByteArray,
    message: AccBroadcastingInbound,
    messageSender: MessageSender,
  ) {
    if (message.msgType() != AccBroadcastingInbound.InboundMsgType.TRACK_DATA) return
    val td = message.body() as AccBroadcastingInbound.TrackData
    val name = td.trackName().data()
    if (name != lastTrack) {
      println("Track changed: $lastTrack → $name")
      lastTrack = name
    }
  }
}
```

Wire it anywhere in the listener list — doesn't depend on `ClientContext`.

## Recipe 2 — Lap tracker (per car)

Records every completed lap into a map. Demonstrates `SessionEventListener` + reading `ClientContext`.

```kotlin
package myapp

import com.github.prule.acc.client.*
import com.github.prule.acc.messages.AccBroadcastingInbound

class LapTracker(private val context: ClientContext) : SessionEventListener {

  data class Lap(val carId: Int, val driver: String, val timeMs: Int, val message: String)

  val laps = mutableListOf<Lap>()

  override fun onSessionStart(preamble: SessionPreamble) {
    laps.clear()
    println("Tracking laps for ${preamble.cars.size} cars at ${preamble.track.name}")
  }

  override fun onSessionMessage(
    bytes: ByteArray,
    message: AccBroadcastingInbound,
    sender: MessageSender,
  ) {
    if (message.msgType() != AccBroadcastingInbound.InboundMsgType.BROADCASTING_EVENT) return
    val evt = message.body() as AccBroadcastingInbound.BroadcastingEvent
    if (evt.type().toString() != "LAP_COMPLETED") return

    val carId = evt.carId()
    val car = context.cars[carId] ?: return
    val driver = car.drivers.getOrNull(car.currentDriverIndex)
    val driverName = driver?.let { "${it.firstName} ${it.lastName}" } ?: "?"
    val timeStr = evt.msg().data()

    laps += Lap(carId, driverName, evt.timeMs(), timeStr)
    println("Lap: car=$carId $driverName $timeStr")
  }

  override fun onSessionStop() {
    println("Session ended. ${laps.size} laps recorded.")
  }
}
```

Wiring:

```kotlin
val context = ClientContext()
AccClient(config).connect(
  listOf(
    LoggingListener(),
    ContextUpdater(context),
    SessionDetector(context, listOf(LapTracker(context))),
  )
)
```

`ContextUpdater` runs first → cars are populated → `LapTracker` sees them.

## Recipe 3 — Telemetry exporter (focused car only)

Streams the focused car's `REALTIME_CAR_UPDATE` to a file. Uses `FilteredMessageListener` to skip unrelated cars.

```kotlin
package myapp

import com.github.prule.acc.client.*
import com.github.prule.acc.messages.AccBroadcastingInbound
import java.io.File

class FocusedCarTelemetry(
  private val context: ClientContext,
  outputFile: File,
) : MessageListener<AccBroadcastingInbound.RealtimeCarUpdate> {

  private val out = outputFile.bufferedWriter()

  override fun onStart() {
    out.write("kmh,gear,worldX,worldY,laps\n")
  }

  override fun onMessage(
    bytes: ByteArray,
    message: AccBroadcastingInbound.RealtimeCarUpdate,
    messageSender: MessageSender,
  ) {
    val focused = context.focusedCarIndex ?: return  // no focus set yet
    if (message.carIndex() != focused) return
    out.write("${message.kmh()},${message.gear()},${message.worldPosX()},${message.worldPosY()},${message.laps()}\n")
  }

  override fun onStop() {
    out.flush()
    out.close()
  }
}
```

Wiring:

```kotlin
val context = ClientContext()
val telemetry = FocusedCarTelemetry(context, File("./telemetry.csv"))

AccClient(config).connect(
  listOf(
    ContextUpdater(context),
    FilteredMessageListener<AccBroadcastingInbound.RealtimeCarUpdate>(
      listeners = listOf(telemetry),
    ),
    SessionDetector(context, listOf(...)),
  )
)
```

`FilteredMessageListener` unwraps the body to `RealtimeCarUpdate` and only forwards matching messages. Saves the inner listener from doing its own type check.

## Recipe 4 — Phase-change observer

Watch every phase transition, not just the active/end pairs that `SessionDetector` cares about.

```kotlin
package myapp

import com.github.prule.acc.client.MessageListener
import com.github.prule.acc.client.MessageSender
import com.github.prule.acc.messages.AccBroadcastingInbound

class PhaseObserver : MessageListener<AccBroadcastingInbound> {
  private var current: String? = null

  override fun onMessage(
    bytes: ByteArray,
    message: AccBroadcastingInbound,
    messageSender: MessageSender,
  ) {
    if (message.msgType() != AccBroadcastingInbound.InboundMsgType.REALTIME_UPDATE) return
    val ru = message.body() as AccBroadcastingInbound.RealtimeUpdate
    val phase = ru.phase().toString()
    if (phase != current) {
      println("Phase: $current → $phase")
      current = phase
    }
  }
}
```

Useful for debugging session transitions or building UIs that mirror ACC's session screen.

## Recipe 5 — Conditional recorder

Only record races, skip practice and qualifying. Demonstrates wrapping `RecordingSessionListener` with a guard.

```kotlin
package myapp

import com.github.prule.acc.client.*
import com.github.prule.acc.messages.AccBroadcastingInbound
import java.nio.file.Path

class RaceOnlyRecorder(directory: Path) : SessionEventListener {
  private val delegate = RecordingSessionListener(directory)
  private var recording = false
  private var currentSessionType: String? = null

  override fun onSessionStart(preamble: SessionPreamble) {
    // Decide later — we don't know the session type until first REALTIME_UPDATE.
  }

  override fun onSessionMessage(
    bytes: ByteArray,
    message: AccBroadcastingInbound,
    sender: MessageSender,
  ) {
    if (!recording &&
        message.msgType() == AccBroadcastingInbound.InboundMsgType.REALTIME_UPDATE) {
      val ru = message.body() as AccBroadcastingInbound.RealtimeUpdate
      val type = ru.sessionType()?.toString()
      if (type != currentSessionType) {
        currentSessionType = type
        if (type == "RACE") {
          // Reconstruct preamble snapshot — we missed the original onSessionStart trigger
          // because we delegated late. For real use, store the preamble from onSessionStart.
          recording = true
        }
      }
    }
    if (recording) delegate.onSessionMessage(bytes, message, sender)
  }

  override fun onSessionStop() {
    if (recording) delegate.onSessionStop()
    recording = false
    currentSessionType = null
  }
}
```

A cleaner production version would store `preamble` from `onSessionStart` and only call `delegate.onSessionStart(preamble)` once the session type is known.

## Recipe 6 — Detect car position changes

Maintain a leaderboard, log overtakes.

```kotlin
package myapp

import com.github.prule.acc.client.*
import com.github.prule.acc.messages.AccBroadcastingInbound

class OvertakeDetector(private val context: ClientContext) : SessionEventListener {
  private val positions = mutableMapOf<Int, Int>()

  override fun onSessionStart(preamble: SessionPreamble) {
    positions.clear()
  }

  override fun onSessionMessage(
    bytes: ByteArray,
    message: AccBroadcastingInbound,
    sender: MessageSender,
  ) {
    if (message.msgType() != AccBroadcastingInbound.InboundMsgType.REALTIME_CAR_UPDATE) return
    val u = message.body() as AccBroadcastingInbound.RealtimeCarUpdate
    val carId = u.carIndex()
    val newPos = u.position()
    val oldPos = positions[carId]
    positions[carId] = newPos
    if (oldPos != null && newPos < oldPos) {
      val car = context.cars[carId] ?: return
      println("Overtake: car #${car.raceNumber} (${car.teamName}) P$oldPos → P$newPos")
    }
  }
}
```

## Recipe 7 — Reply to the server (rare)

The framework already handles the only outbounds normally needed (entry list + track data refresh). If you want to use other commands:

```kotlin
package myapp

import com.github.prule.acc.client.*
import com.github.prule.acc.messages.AccBroadcastingClient
import com.github.prule.acc.messages.AccBroadcastingInbound

class FocusedCarSetter(
  private val context: ClientContext,
  private val targetCarId: Int,
) : MessageListener<AccBroadcastingInbound> {

  private val client = AccBroadcastingClient()
  private var sent = false

  override fun onMessage(
    bytes: ByteArray,
    message: AccBroadcastingInbound,
    messageSender: MessageSender,
  ) {
    if (sent) return
    if (message.msgType() != AccBroadcastingInbound.InboundMsgType.REALTIME_UPDATE) return
    if (context.connectionId == 0) return
    // Construct your outbound packet from AccBroadcastingClient.
    // (Method names depend on the acc-messages version — see that repo.)
    // val packet = client.buildChangeFocus(context.connectionId, targetCarId, ...)
    // messageSender.send(packet)
    sent = true
  }
}
```

Note: most command outbounds require the `commandPassword` to match. See [WireProtocol.md](WireProtocol.md#read-only-mode).

## Anti-patterns

| Don't | Why |
|---|---|
| Mutate `ClientContext` from outside `ContextUpdater` | Race conditions; ordering bugs; future refactors will break you. |
| Block in `onMessage` (network calls, file IO without buffering, `Thread.sleep`) | Stalls the receive loop; subsequent messages queue in the OS UDP buffer until it overflows and packets drop. |
| Throw exceptions from `onMessage` to "skip" a message | Kills the receive loop. Use `try/catch` and `return`. |
| Hold references to the parsed `AccBroadcastingInbound` past `onMessage` return | The Kaitai stream may be reused — defensive copy first if you need to retain. |
| Read `ClientContext.cars` and `ClientContext.track` separately and assume they're consistent | Track change between reads can give you a new track + old cars. Use `snapshotRawPreamble()` or copy `cars.toMap()` first. |
| Register the same listener twice | `onMessage` fires twice per packet for that listener. Probably not what you want. |

## Testing your listener

See [Testing.md](Testing.md) for fixture patterns — using mockk for behavior tests and real recorded UDP bytes from the playback CSV for parser-dependent paths.

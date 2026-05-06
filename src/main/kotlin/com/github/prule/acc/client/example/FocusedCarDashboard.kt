package com.github.prule.acc.client.example

import com.github.prule.acc.client.AccClient
import com.github.prule.acc.client.AccClientConfiguration
import com.github.prule.acc.client.CarModelRepository
import com.github.prule.acc.client.ClientContext
import com.github.prule.acc.client.ContextUpdater
import com.github.prule.acc.client.MessageListener
import com.github.prule.acc.client.MessageSender
import com.github.prule.acc.messages.AccBroadcastingInbound

/**
 * Live single-line CLI dashboard showing what the ACC broadcasting view is currently focused on.
 *
 * Output (refreshed in place via `\r`):
 *
 * ```
 * [Red Bull Ring] focused=#13  Mercedes-AMG GT3 Evo            G:2   120kmh  lap  11.9%
 * ```
 *
 * Demonstrates:
 * - Construction of [ClientContext] and wiring with [ContextUpdater].
 * - A custom [MessageListener] that reads decoded state from [ClientContext].
 * - Looking up the human-readable car model name via [CarModelRepository].
 * - Pulling per-tick fields from [AccBroadcastingInbound.RealtimeCarUpdate].
 *
 * Run with `./gradlew runFocusedCarDashboard` against a running ACC server (or
 * `./gradlew runAccSimulator` in another terminal for offline playback).
 */
class FocusedCarDashboard(
  private val context: ClientContext,
  private val carModels: CarModelRepository = CarModelRepository(),
) : MessageListener<AccBroadcastingInbound> {

  // Last-seen per-tick values for the focused car. Updated only on REALTIME_CAR_UPDATE
  // for the matching carIndex.
  @Volatile private var gear: Int = 1 // ACC convention: 0=R, 1=N, 2=1st...
  @Volatile private var kmh: Int = 0
  @Volatile private var spline: Float = 0f
  @Volatile private var laps: Int = 0

  override fun onMessage(
    bytes: ByteArray,
    message: AccBroadcastingInbound,
    messageSender: MessageSender,
  ) {
    when (message.msgType()) {
      AccBroadcastingInbound.InboundMsgType.REALTIME_CAR_UPDATE -> {
        val u = message.body() as AccBroadcastingInbound.RealtimeCarUpdate
        val focused = context.focusedCarIndex ?: return // no focus set yet
        if (u.carIndex() == focused) {
          gear = numeric(u, "gear")
          kmh = numeric(u, "kmh")
          spline = floating(u, "splinePosition")
          laps = numeric(u, "laps")
          render()
        }
      }
      // Re-render on any state-changing preamble or focus change so the line
      // refreshes promptly without waiting for the next car update tick.
      AccBroadcastingInbound.InboundMsgType.REALTIME_UPDATE,
      AccBroadcastingInbound.InboundMsgType.TRACK_DATA,
      AccBroadcastingInbound.InboundMsgType.ENTRY_LIST,
      AccBroadcastingInbound.InboundMsgType.ENTRY_LIST_CAR -> render()
      else -> {
        // ignore
      }
    }
  }

  override fun onStart() {
    println("Focused-car dashboard — Ctrl-C to stop")
  }

  override fun onStop() {
    println() // move past the in-place line so terminal prompt isn't overwritten
    println("Disconnected")
  }

  private fun render() {
    val track = context.track?.name ?: "—"
    val idx = context.focusedCarIndex
    val idxLabel = idx?.toString() ?: "—"
    val car = idx?.let { context.cars[it] }
    val carName = car?.let { carModels.findById(it.carModelType)?.name } ?: "?"
    val gearLabel =
      when (gear) {
        0 -> "R"
        1 -> "N"
        else -> (gear - 1).toString()
      }
    val lapPct = (spline * 100f).coerceIn(0f, 100f)
    // Padded fields keep the line a fixed width so `\r` overwrites cleanly.
    val line =
      "[%-20s] focused=#%-3s %-30s G:%-2s %3dkmh  L%-3d lap %5.1f%%   ".format(
        truncate(track, 20),
        idxLabel,
        truncate(carName, 30),
        gearLabel,
        kmh,
        laps,
        lapPct,
      )
    print("\r$line")
    System.out.flush()
  }

  private fun truncate(s: String, max: Int): String =
    if (s.length <= max) s else s.substring(0, max - 1) + "…"

  // Reflection helpers — defensive against acc-messages API drift.
  // The kaitai-generated method names should be `gear()`, `kmh()`, `splinePosition()`,
  // but we read them via reflection so a name change degrades to zero rather than crashing.
  private fun numeric(target: Any, methodName: String): Int =
    runCatching {
        val v = target::class.java.getMethod(methodName).invoke(target)
        (v as Number).toInt()
      }
      .getOrDefault(0)

  private fun floating(target: Any, methodName: String): Float =
    runCatching {
        val v = target::class.java.getMethod(methodName).invoke(target)
        (v as Number).toFloat()
      }
      .getOrDefault(0f)
}

/**
 * Runnable entrypoint. Connects to a local ACC server (or the simulator) on port 9000.
 *
 * Edit [AccClientConfiguration] to point at a different host or port. Defaults match
 * `./gradlew runAccSimulator` so you can prove the wiring without a running game.
 */
suspend fun main() {
  val context = ClientContext()
  AccClient(
      AccClientConfiguration(
        name = "Dashboard",
        port = 9000,
        serverIp = "127.0.0.1",
      )
    )
    .connect(
      listOf(
        ContextUpdater(context),
        FocusedCarDashboard(context),
      )
    )
}

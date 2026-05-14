package com.github.prule.acc.client.simulator

import ch.qos.logback.core.encoder.ByteArrayUtil.hexStringToByteArray
import com.github.prule.acc.client.MessageSender
import com.github.prule.acc.client.ProgressReporter
import com.github.prule.acc.messages.AccBroadcastingInbound
import io.kaitai.struct.ByteBufferKaitaiStream
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Plays back events from CSV source by sending each row as a message. Note, it doesn't send at the
 * same rate it was recorded - it just uses a standard delay between each message.
 *
 * The most recent `REALTIME_UPDATE` bytes are retained so [emitSessionOver] can synthesize a
 * graceful session-end frame (same packet with the phase byte rewritten to `SESSION_OVER`) when the
 * simulator is stopped.
 */
class EventPlayer(
  val eventsFile: Source,
  val millisDelay: Long = 100,
  val maxEvents: Int = Int.MAX_VALUE,
  /**
   * If true, suppress `REALTIME_CAR_UPDATE` messages for cars other than the focused car. Useful
   * when you want a quieter stream for single-car testing - but at the cost of long stretches of
   * silence whenever the focused car's tick is sparse, which can trip `AccClient`'s 2s read
   * timeout. Defaults to `false` so the simulator emits a steady stream of car updates.
   */
  val onlyPlayerEvents: Boolean = false,
) {
  private val playbackEventsRepository = PlaybackEventsRepository()
  private val logger = LoggerFactory.getLogger(javaClass)

  @Volatile private var sender: MessageSender? = null
  @Volatile private var lastRealtimeUpdate: ByteArray? = null
  @Volatile private var currentJob: Job? = null

  /**
   * Start playback for the given client. Mirrors real ACC: only one session is active at a time, so
   * if a previous playback is still running, this emits a final `SESSION_OVER` to the previous
   * client and cancels its playback before launching the new one.
   */
  @Synchronized
  fun sendPackets(scope: CoroutineScope, messageSender: MessageSender): Job {
    val previous = currentJob
    if (previous != null && previous.isActive) {
      logger.info("New client registered while a session was running; ending current session")
      emitSessionOver()
      previous.cancel()
    }
    sender = messageSender
    lastRealtimeUpdate = null
    val job = scope.launch {
      val events = playbackEventsRepository.load(eventsFile)
      val progressReporter = ProgressReporter(events.size, 10)
      var focussedCar: Int? = null

      events.take(maxEvents).forEachIndexed { index, row ->
        val bytes = row.hex.hexToByteArray()
        val type = row.type.toLong()

        if (type == AccBroadcastingInbound.InboundMsgType.REALTIME_UPDATE.id()) {
          val data: ByteArray = hexStringToByteArray(row.hex)
          val stream = ByteBufferKaitaiStream(data)
          val packet = AccBroadcastingInbound(stream)
          focussedCar = (packet.body() as AccBroadcastingInbound.RealtimeUpdate).focusedCarIndex()
          lastRealtimeUpdate = bytes
          trySend(bytes, messageSender)
        } else if (
          !onlyPlayerEvents ||
            focussedCar == null ||
            type != AccBroadcastingInbound.InboundMsgType.REALTIME_CAR_UPDATE.id()
        ) {
          trySend(bytes, messageSender)
        } else {
          val data: ByteArray = hexStringToByteArray(row.hex)
          val stream = ByteBufferKaitaiStream(data)
          val packet = AccBroadcastingInbound(stream)
          val carIndex = (packet.body() as AccBroadcastingInbound.RealtimeCarUpdate).carIndex()
          if (carIndex == focussedCar) {
            trySend(bytes, messageSender)
          }
        }

        logger.trace("Sending")
        progressReporter.report(index + 1)
        delay(millisDelay.milliseconds)
      }
      logger.info("Event player finished {}", eventsFile)
    }
    currentJob = job
    return job
  }

  /**
   * Send a final `REALTIME_UPDATE` with phase=`SESSION_OVER`, derived from the most recent realtime
   * update emitted during playback. No-op if no realtime update was ever sent (e.g. simulator
   * stopped before the client registered).
   *
   * Wire layout - byte 0 is the msg type, then event_index (u2) + session_index (u2) + session_type
   * (u1) + phase (u1). So the phase byte sits at index 6.
   */
  fun emitSessionOver() {
    val s = sender ?: return
    val template = lastRealtimeUpdate ?: return
    val out = template.copyOf()
    out[PHASE_BYTE_OFFSET] = SESSION_OVER_PHASE
    try {
      s.send(out)
    } catch (e: Exception) {
      logger.debug("Suppressed error sending SESSION_OVER frame: {}", e.message)
    }
  }

  private fun trySend(bytes: ByteArray, target: MessageSender) {
    try {
      target.send(bytes)
    } catch (e: Exception) {
      // Socket may have been closed by AccSimulator.stop(); playback is about to be cancelled.
      logger.debug("Playback send failed (socket likely closed): {}", e.message)
    }
  }

  companion object {
    const val PHASE_BYTE_OFFSET = 6
    const val SESSION_OVER_PHASE: Byte = 6
  }
}

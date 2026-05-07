package com.github.prule.acc.client

import com.github.prule.acc.messages.AccBroadcastingInbound
import java.time.Instant
import org.slf4j.LoggerFactory

/**
 * Detects session phase transitions in REALTIME_UPDATE messages and fires session lifecycle events
 * on [SessionEventListener]s.
 *
 * Reads track + car preamble from [ClientContext] (populated by [ContextUpdater]). Snapshots that
 * context on session start so listeners receive an immutable [SessionPreamble].
 *
 * If the phase transitions to active before preamble is ready (no track or no cars), the start is
 * deferred until the next preamble update arrives. This prevents firing onSessionStart with a
 * partial snapshot.
 *
 * MUST be installed AFTER [ContextUpdater] in the listener list — otherwise the snapshot would lag
 * by one message.
 */
class SessionDetector(
  private val context: ClientContext,
  private val listeners: List<SessionEventListener>,
) : MessageListener<AccBroadcastingInbound> {
  private val logger = LoggerFactory.getLogger(javaClass)

  companion object {
    private val SESSION_ACTIVE_PHASES = setOf("FORMATION_LAP", "SESSION")
    private val SESSION_END_PHASES = setOf("SESSION_OVER", "POST_SESSION")
  }

  private var sessionActive = false
  private var pendingStart = false

  override fun onMessage(
    bytes: ByteArray,
    message: AccBroadcastingInbound,
    messageSender: MessageSender,
  ) {
    val type = message.msgType()

    // Preamble updates may unblock a deferred session start.
    if (pendingStart && isPreambleType(type) && context.isPreambleReady()) {
      logger.debug("Preamble ready; firing deferred onSessionStart")
      pendingStart = false
      fireSessionStart()
    }

    if (type == AccBroadcastingInbound.InboundMsgType.REALTIME_UPDATE) {
      val phase = (message.body() as AccBroadcastingInbound.RealtimeUpdate).phase().toString()

      if (!sessionActive && phase in SESSION_ACTIVE_PHASES) {
        if (context.isPreambleReady()) {
          logger.debug("Session started (phase={})", phase)
          fireSessionStart()
        } else {
          logger.debug("Session start deferred — preamble not ready (phase={})", phase)
          pendingStart = true
        }
      } else if (sessionActive && phase in SESSION_END_PHASES) {
        logger.debug("Session ended (phase={})", phase)
        sessionActive = false
        listeners.forEach { it.onSessionStop() }
      }
    }

    if (sessionActive) {
      listeners.forEach { it.onSessionMessage(bytes, message, messageSender) }
    }
  }

  override fun onStop() {
    pendingStart = false
    if (sessionActive) {
      logger.debug("Connection lost during active session")
      sessionActive = false
      listeners.forEach { it.onSessionStop() }
    }
  }

  private fun fireSessionStart() {
    val track = context.track ?: return
    val preamble =
      SessionPreamble(
        track = track,
        cars = context.cars.toMap(),
        entryListVersion = context.entryListVersion,
        capturedAt = Instant.now(),
        raw = context.snapshotRawPreamble(),
      )
    sessionActive = true
    listeners.forEach { it.onSessionStart(preamble) }
  }

  private fun isPreambleType(type: AccBroadcastingInbound.InboundMsgType): Boolean =
    type == AccBroadcastingInbound.InboundMsgType.TRACK_DATA ||
      type == AccBroadcastingInbound.InboundMsgType.ENTRY_LIST ||
      type == AccBroadcastingInbound.InboundMsgType.ENTRY_LIST_CAR
}

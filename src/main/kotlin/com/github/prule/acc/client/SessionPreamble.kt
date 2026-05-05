package com.github.prule.acc.client

import com.github.prule.acc.messages.AccBroadcastingInbound
import java.time.Instant

/**
 * Snapshot of [ClientContext] preamble at the moment a session started. Decoupled from the live
 * context so consumers can rely on values not mutating mid-session.
 *
 * @property track decoded track info as observed at session start
 * @property cars decoded car entries by carId
 * @property entryListVersion monotonic counter from [ClientContext]; bumped on track change or new
 *   ENTRY_LIST. Useful for diffing across sessions.
 * @property capturedAt wall-clock time the snapshot was taken
 * @property raw last-seen raw bytes for each preamble message — for byte-perfect replay /
 *   recording
 */
data class SessionPreamble(
  val track: TrackInfo,
  val cars: Map<Int, CarEntry>,
  val entryListVersion: Long,
  val capturedAt: Instant,
  val raw: RawPreamble,
)

/** Raw bytes + parsed message pair. Retained for back-compat with consumers that need both. */
data class PreambleMessage(val bytes: ByteArray, val message: AccBroadcastingInbound) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is PreambleMessage) return false
    return bytes.contentEquals(other.bytes) && message === other.message
  }

  override fun hashCode(): Int = 31 * bytes.contentHashCode() + System.identityHashCode(message)
}

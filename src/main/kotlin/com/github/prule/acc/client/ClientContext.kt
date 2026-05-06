package com.github.prule.acc.client

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Cross-session client state. Owned by the user, populated by [ContextUpdater], read by
 * [SessionDetector] and consumer code. Survives ACC server reconnects within a single [AccClient]
 * lifetime.
 *
 * Thread model: [MessageReceiver] dispatches messages on a single coroutine, so writes from
 * [ContextUpdater] are serialized. External readers may be on other threads — scalar fields are
 * `@Volatile` and [cars] / [rawCarEntries] use [ConcurrentHashMap].
 */
class ClientContext {
  @Volatile var connectionId: Int = 0
  /**
   * Index of the car currently focused by ACC's broadcasting view. `null` until the first
   * `REALTIME_UPDATE` arrives — distinguishes "no value yet" from a real focused carId of 0.
   */
  @Volatile var focusedCarIndex: Int? = null
  @Volatile var track: TrackInfo? = null
  @Volatile var entryListVersion: Long = 0
  @Volatile var lastPreambleAt: Instant? = null

  /** Decoded car entries, keyed by carId. */
  val cars: MutableMap<Int, CarEntry> = ConcurrentHashMap()

  /** Raw bytes for replay/recording. Synchronize on [rawLock] for atomic reads. */
  @Volatile var rawTrackData: ByteArray? = null
  @Volatile var rawEntryList: ByteArray? = null
  val rawCarEntries: MutableMap<Int, ByteArray> = ConcurrentHashMap()
  internal val rawLock = Any()

  /** True when at least track data and one car entry have been observed. */
  fun isPreambleReady(): Boolean = track != null && cars.isNotEmpty()

  /** Atomic snapshot of the raw preamble suitable for replay. */
  fun snapshotRawPreamble(): RawPreamble =
    synchronized(rawLock) {
      RawPreamble(
        trackData = rawTrackData?.copyOf(),
        entryList = rawEntryList?.copyOf(),
        carEntries = rawCarEntries.mapValues { it.value.copyOf() },
      )
    }

  override fun toString(): String =
    "ClientContext(connectionId=$connectionId, focusedCarIndex=$focusedCarIndex, " +
      "track=${track?.name}, cars=${cars.size}, entryListVersion=$entryListVersion)"
}


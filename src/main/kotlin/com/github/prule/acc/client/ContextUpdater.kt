package com.github.prule.acc.client

import com.github.prule.acc.messages.AccBroadcastingClient
import com.github.prule.acc.messages.AccBroadcastingInbound
import java.time.Instant
import org.slf4j.LoggerFactory

/**
 * Owns all mutations to [ClientContext]. Decodes preamble messages
 * ([AccBroadcastingInbound.InboundMsgType.TRACK_DATA],
 * [AccBroadcastingInbound.InboundMsgType.ENTRY_LIST],
 * [AccBroadcastingInbound.InboundMsgType.ENTRY_LIST_CAR]) into stable domain objects and caches
 * them in [context]. Cache survives ACC server reconnects within a single [AccClient] lifetime.
 *
 * On [AccBroadcastingInbound.InboundMsgType.REGISTRATION_RESULT], records the new
 * [ClientContext.connectionId] and requests fresh entry list + track data from the server.
 * Refresh-on-registration keeps the cache current after every reconnect without requiring the
 * caller to manage retransmits.
 *
 * Track-name change → all cached cars are evicted, since car ids are only meaningful within a
 * single track session.
 *
 * MUST be installed before [SessionDetector] in the listener list so that [ClientContext] is up to
 * date when [SessionDetector] snapshots it on session start.
 */
class ContextUpdater(private val context: ClientContext) :
  MessageListener<AccBroadcastingInbound> {
  private val logger = LoggerFactory.getLogger(javaClass)
  private val client = AccBroadcastingClient()

  override fun onMessage(
    bytes: ByteArray,
    message: AccBroadcastingInbound,
    messageSender: MessageSender,
  ) {
    when (message.msgType()) {
      AccBroadcastingInbound.InboundMsgType.REGISTRATION_RESULT -> {
        val body = message.body() as AccBroadcastingInbound.RegistrationResult
        context.connectionId = body.connectionId()
        logger.debug("Registered: connectionId={}", context.connectionId)
        // Refresh preamble. Server re-sends so cache stays current after every reconnect.
        messageSender.send(client.buildRequestEntryList(context.connectionId))
        messageSender.send(client.buildRequestTrackData(context.connectionId))
      }

      AccBroadcastingInbound.InboundMsgType.TRACK_DATA -> {
        val body = message.body() as AccBroadcastingInbound.TrackData
        val info = decodeTrack(body)
        val previous = context.track
        if (previous != null && previous.name != info.name) {
          logger.info(
            "Track changed: {} -> {}; clearing {} cached cars",
            previous.name,
            info.name,
            context.cars.size,
          )
          context.cars.clear()
          synchronized(context.rawLock) { context.rawCarEntries.clear() }
          context.entryListVersion += 1
        }
        context.track = info
        synchronized(context.rawLock) { context.rawTrackData = bytes.copyOf() }
        context.lastPreambleAt = Instant.now()
        logger.debug("Track data cached: {}", info.name)
      }

      AccBroadcastingInbound.InboundMsgType.ENTRY_LIST -> {
        val body = message.body() as AccBroadcastingInbound.EntryList
        val expectedIds: Set<Int> = body.carIndexes().map { (it as Number).toInt() }.toSet()
        // Evict cars no longer in the list. New cars arrive via subsequent ENTRY_LIST_CAR.
        val evicted = context.cars.keys.filter { it !in expectedIds }
        if (evicted.isNotEmpty()) {
          logger.debug("Evicting cars not in new entry list: {}", evicted)
          evicted.forEach {
            context.cars.remove(it)
            synchronized(context.rawLock) { context.rawCarEntries.remove(it) }
          }
        }
        synchronized(context.rawLock) { context.rawEntryList = bytes.copyOf() }
        context.entryListVersion += 1
        context.lastPreambleAt = Instant.now()
        logger.debug(
          "Entry list cached: {} cars, version={}",
          expectedIds.size,
          context.entryListVersion,
        )
      }

      AccBroadcastingInbound.InboundMsgType.ENTRY_LIST_CAR -> {
        val body = message.body() as AccBroadcastingInbound.EntryListCar
        val entry = decodeCar(body)
        context.cars[entry.carId] = entry
        synchronized(context.rawLock) { context.rawCarEntries[entry.carId] = bytes.copyOf() }
        context.lastPreambleAt = Instant.now()
        logger.debug("Car entry cached: carId={}", entry.carId)
      }

      AccBroadcastingInbound.InboundMsgType.REALTIME_UPDATE -> {
        val body = message.body() as AccBroadcastingInbound.RealtimeUpdate
        context.focusedCarIndex = body.focusedCarIndex()
      }

      else -> {
        // ignore — non-preamble, non-state-bearing messages
      }
    }
  }

  private fun decodeTrack(body: AccBroadcastingInbound.TrackData): TrackInfo =
    TrackInfo(
      name = body.trackName().data(),
      id = runCatching { numeric(body, "trackId") }.getOrDefault(0),
      meters = runCatching { numeric(body, "trackMeters") }.getOrDefault(0),
      cameraSets = runCatching { decodeCameraSets(body) }.getOrDefault(emptyList()),
      hudPages = runCatching { decodeHudPages(body) }.getOrDefault(emptyList()),
    )

  @Suppress("UNCHECKED_CAST")
  private fun decodeCameraSets(body: AccBroadcastingInbound.TrackData): List<TrackInfo.CameraSet> {
    val sets = body::class.java.getMethod("cameraSets").invoke(body) as Iterable<Any>
    return sets.map { set ->
      val nameWrap = set::class.java.getMethod("cameraSetName").invoke(set)
      val name = nameWrap::class.java.getMethod("data").invoke(nameWrap) as String
      val cams = set::class.java.getMethod("cameras").invoke(set) as Iterable<Any>
      TrackInfo.CameraSet(
        name = name,
        cameras = cams.map { c -> c::class.java.getMethod("data").invoke(c) as String },
      )
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun decodeHudPages(body: AccBroadcastingInbound.TrackData): List<String> {
    val pages = body::class.java.getMethod("hudPages").invoke(body) as Iterable<Any>
    return pages.map { p -> p::class.java.getMethod("data").invoke(p) as String }
  }

  private fun decodeCar(body: AccBroadcastingInbound.EntryListCar): CarEntry =
    CarEntry(
      carId = body.carId(),
      carModelType = runCatching { numeric(body, "carModelType") }.getOrDefault(0),
      teamName = runCatching { stringWrap(body, "teamName") }.getOrDefault(""),
      raceNumber = runCatching { numeric(body, "raceNumber") }.getOrDefault(0),
      cupCategory = runCatching { enumName(body, "cupCategory") }.getOrDefault(""),
      currentDriverIndex = runCatching { numeric(body, "driverIndex") }.getOrDefault(0),
      nationality = runCatching { numeric(body, "nationality") }.getOrDefault(0),
      drivers = runCatching { decodeDrivers(body) }.getOrDefault(emptyList()),
    )

  @Suppress("UNCHECKED_CAST")
  private fun decodeDrivers(body: AccBroadcastingInbound.EntryListCar): List<CarEntry.Driver> {
    val drivers = body::class.java.getMethod("drivers").invoke(body) as Iterable<Any>
    return drivers.map { d ->
      CarEntry.Driver(
        firstName = runCatching { stringWrap(d, "firstName") }.getOrDefault(""),
        lastName = runCatching { stringWrap(d, "lastName") }.getOrDefault(""),
        shortName = runCatching { stringWrap(d, "shortName") }.getOrDefault(""),
        category = runCatching { enumName(d, "category") }.getOrDefault(""),
        nationality = runCatching { numeric(d, "nationality") }.getOrDefault(0),
      )
    }
  }

  private fun stringWrap(target: Any, methodName: String): String {
    val wrap = target::class.java.getMethod(methodName).invoke(target) ?: return ""
    return wrap::class.java.getMethod("data").invoke(wrap) as String
  }

  private fun numeric(target: Any, methodName: String): Int {
    val v = target::class.java.getMethod(methodName).invoke(target) ?: return 0
    return (v as Number).toInt()
  }

  private fun enumName(target: Any, methodName: String): String =
    target::class.java.getMethod(methodName).invoke(target)?.toString().orEmpty()
}

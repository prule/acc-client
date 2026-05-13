package com.github.prule.acc.client.simulator

import ch.qos.logback.core.encoder.ByteArrayUtil.hexStringToByteArray
import com.github.prule.acc.client.MessageSender
import com.github.prule.acc.client.ProgressReporter
import com.github.prule.acc.messages.AccBroadcastingInbound
import io.kaitai.struct.ByteBufferKaitaiStream
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Plays back events from CSV source by sending each row as a message. Note, it doesn't send at the
 * same rate it was recorded - it just uses a standard delay between each message.
 */
class EventPlayer(
  val eventsFile: Source,
  val millisDelay: Long = 100,
  val maxEvents: Int = Int.MAX_VALUE,
) {
  private val playbackEventsRepository = PlaybackEventsRepository()
  private val logger = LoggerFactory.getLogger(javaClass)

  @OptIn(DelicateCoroutinesApi::class)
  fun sendPackets(messageSender: MessageSender) {
    GlobalScope.launch {
      val events = playbackEventsRepository.load(eventsFile)
      val progressReporter = ProgressReporter(events.size, 10)
      var focussedCar: Int? = null

      events.take(maxEvents).forEachIndexed { index, row ->
        // realtime update
        if (row.type.toLong() == AccBroadcastingInbound.InboundMsgType.REALTIME_UPDATE.id()) {
          val data: ByteArray = hexStringToByteArray(row.hex)
          val stream = ByteBufferKaitaiStream(data)
          val packet = AccBroadcastingInbound(stream)
          focussedCar = (packet.body() as AccBroadcastingInbound.RealtimeUpdate).focusedCarIndex()
        }

        if (
          focussedCar == null ||
            row.type.toLong() != AccBroadcastingInbound.InboundMsgType.REALTIME_CAR_UPDATE.id()
        ) {
          messageSender.send(row.hex.hexToByteArray())
        } else {
          val data: ByteArray = hexStringToByteArray(row.hex)
          val stream = ByteBufferKaitaiStream(data)
          val packet = AccBroadcastingInbound(stream)
          val carIndex = (packet.body() as AccBroadcastingInbound.RealtimeCarUpdate).carIndex()
          if (carIndex == focussedCar) {
            messageSender.send(row.hex.hexToByteArray())
          }
        }

        progressReporter.report(index + 1)
        Thread.sleep(millisDelay)
      }
      logger.info("Event player finished {}", eventsFile)
    }
  }
}

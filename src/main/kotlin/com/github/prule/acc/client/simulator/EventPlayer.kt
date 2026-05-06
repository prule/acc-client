package com.github.prule.acc.client.simulator

import com.github.prule.acc.client.MessageSender
import com.github.prule.acc.client.ProgressReporter
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Plays back events from CSV source by sending each row as a message. Note, it doesn't send at the
 * same rate it was recorded - it just uses a standard delay between each message.
 */
class EventPlayer(val eventsFile: Source, val millisDelay: Long = 100) {
  private val playbackEventsRepository = PlaybackEventsRepository()
  private val logger = LoggerFactory.getLogger(javaClass)

  @OptIn(DelicateCoroutinesApi::class)
  fun sendPackets(messageSender: MessageSender) {
    GlobalScope.launch {
      val events = playbackEventsRepository.load(eventsFile)
      val progressReporter = ProgressReporter(events.size, 10)
      events.forEachIndexed { index, row ->
        messageSender.send(row.hex.hexToByteArray())
        progressReporter.report(index)
        Thread.sleep(millisDelay)
      }
    }
  }
}

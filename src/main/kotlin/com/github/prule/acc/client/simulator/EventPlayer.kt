package com.github.prule.acc.client.simulator

import com.github.prule.acc.client.MessageSender
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class EventPlayer(val eventsFile: Source, val millisDelay: Long = 100) {
  private val playbackEventsRepository = PlaybackEventsRepository()

  @OptIn(DelicateCoroutinesApi::class)
  fun sendPackets(messageSender: MessageSender) {
    GlobalScope.launch {
      val events = playbackEventsRepository.load(eventsFile)
      events.forEach {
        // todo incorporate proper time delay
        messageSender.send(it.hex.hexToByteArray())
        Thread.sleep(millisDelay)
      }
    }
  }
}

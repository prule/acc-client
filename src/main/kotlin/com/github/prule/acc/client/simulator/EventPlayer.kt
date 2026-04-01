package com.github.prule.acc.client.simulator

import com.github.prule.acc.client.MessageSender
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class EventPlayer(
    val eventsFile: Source,
) {
    private val playbackEventsRepository = PlaybackEventsRepository()
    private val logger = LoggerFactory.getLogger(javaClass)

    @OptIn(DelicateCoroutinesApi::class)
    fun sendPackets(messageSender: MessageSender) {
        GlobalScope.launch {
            val events = playbackEventsRepository.load(eventsFile)
            events.forEach {
                // todo incorporate proper time delay
                messageSender.send(it.hex.hexToByteArray())
                Thread.sleep(500)
            }
        }
    }
}

package io.github.prule.acc.client.simulator

import io.github.prule.acc.client.MessageSender
import java.nio.file.Path

class EventPlayer(
    val eventsFile: Path,
) {
    private val playbackEventsRepository = PlaybackEventsRepository()

    fun sendPackets(messageSender: MessageSender) {
        val events = playbackEventsRepository.load(eventsFile.toFile())
        events.forEach {
            // todo incorporate proper time delay
            messageSender.send(it.hex.hexToByteArray())
            Thread.sleep(500)
        }
    }
}

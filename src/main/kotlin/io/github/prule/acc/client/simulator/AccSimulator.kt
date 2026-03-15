package io.github.prule.acc.client.simulator

import io.github.prule.acc.client.MessageReceiver
import io.github.prule.acc.messages.AccBroadcastingOutbound
import org.slf4j.LoggerFactory
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.file.Path

fun main() {
    val configuration = AccSimulatorConfiguration(9996, "asd", Path.of("./playback-events.csv"))
    AccSimulator(configuration).start()
}

/**
 * Simulates ACC by listening for the handshake request and then playing back a pre-recorded session.
 * Useful for development and debugging.
 */
class AccSimulator(
    val configuration: AccSimulatorConfiguration,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private var running = true
    private val socket = DatagramSocket(configuration.port, InetAddress.getByName("0.0.0.0"))

    fun start() {
        logger.debug("Starting simulator")
        MessageReceiver(
            socket,
            listOf(
                RegisterListener(socket, EventPlayer(configuration.playbackEventsFile)),
            ),
        ) { buffer -> AccBroadcastingOutbound(buffer) }
            .start()
    }

    fun stop() {
        running = false
    }
}

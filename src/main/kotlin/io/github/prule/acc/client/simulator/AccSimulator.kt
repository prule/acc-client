package io.github.prule.acc.client.simulator

import io.github.prule.acc.client.LoggingListener
import io.github.prule.acc.client.MessageReceiver
import io.github.prule.acc.messages.AccBroadcastingOutbound
import org.slf4j.LoggerFactory
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Start the simulator with a pre-recorded session (playback-events.csv).
 *
 * Events can be read from the classpath or a local file.
 * ```kotlin
 *   playbackEventsFile = ClasspathSource("io/github/prule/acc/client/simulator/playback-events.csv"),
 *
 *   OR
 *
 *   playbackEventsFile = FileSource("./playback-events.csv"),
 * ```
 */
fun main() {
    AccSimulator(
        AccSimulatorConfiguration(
            port = 9000,
            connectionPassword = "asd",
            playbackEventsFile = ClasspathSource("io/github/prule/acc/client/simulator/playback-events.csv"),
        ),
    ).start()
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
                LoggingListener(),
                RegisterListener(socket, EventPlayer(configuration.playbackEventsFile)),
            ),
        ) { buffer -> AccBroadcastingOutbound(buffer) }
            .start()
    }

    fun stop() {
        running = false
    }
}

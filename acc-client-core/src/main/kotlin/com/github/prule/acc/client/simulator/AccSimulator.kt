package com.github.prule.acc.client.simulator

import com.github.prule.acc.client.LoggingListener
import com.github.prule.acc.client.MessageReceiver
import com.github.prule.acc.messages.AccBroadcastingOutbound
import java.net.DatagramSocket
import java.net.InetAddress
import org.slf4j.LoggerFactory

/**
 * Start the simulator with a pre-recorded session (playback-events.csv).
 *
 * Events can be read from the classpath or a local file.
 *
 * ```kotlin
 *   playbackEventsFile = ClasspathSource("com/github/prule/acc/client/simulator/playback-events.csv"),
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
        playbackEventsFile =
          //          ClasspathSource("com/github/prule/acc/client/simulator/playback-events.csv"),
          //          FileSource("./recordings/playback-events.csv"),
          //
          // FileSource("./recordings/simulator-recording-2026-05-04T18-49-26.591594-race.csv"),
          //          FileSource("./recordings/full-race-donington.csv"),
          FileSource(
            "./recordings/simulator-recording-2026-05-06T11-48-49.206633-race-donington-ferrari.csv"
          ),
      )
    )
    .start()
    .join()
}

/**
 * Simulates ACC by listening for the handshake request and then playing back a pre-recorded
 * session. Useful for development and debugging.
 *
 * `start()` is non-blocking: the receive loop runs on a background thread. Call `join()` on the
 * returned handle to wait for it to finish, or `stop()` to shut it down (closes the socket, which
 * unblocks the receive loop).
 */
class AccSimulator(val configuration: AccSimulatorConfiguration) {
  private val logger = LoggerFactory.getLogger(javaClass)

  @Volatile private var socket: DatagramSocket? = null
  @Volatile private var thread: Thread? = null

  @Synchronized
  fun start(): Handle {
    check(thread == null) { "Simulator already running on port ${configuration.port}" }
    logger.debug("Starting simulator on port ${configuration.port}")
    val newSocket = DatagramSocket(configuration.port, InetAddress.getByName("0.0.0.0"))
    socket = newSocket
    val newThread =
      Thread(
          {
            MessageReceiver(
                newSocket,
                listOf(
                  LoggingListener(),
                  RegisterListener(
                    newSocket,
                    EventPlayer(
                      configuration.playbackEventsFile,
                      configuration.delay,
                      configuration.maxEvents,
                    ),
                  ),
                ),
              ) { buffer ->
                AccBroadcastingOutbound(buffer)
              }
              .start()
          },
          "acc-simulator-${configuration.port}",
        )
        .apply {
          isDaemon = true
          start()
        }
    thread = newThread
    return Handle(newThread)
  }

  /** Stop if running; no-op otherwise. Blocks (up to 5s) until the receive loop exits. */
  @Synchronized
  fun stop() {
    val s = socket ?: return
    logger.debug("Stopping simulator on port ${configuration.port}")
    s.close()
    thread?.join(5_000)
    socket = null
    thread = null
  }

  fun isRunning(): Boolean = thread?.isAlive == true

  class Handle(private val thread: Thread) {
    fun join() = thread.join()
  }
}

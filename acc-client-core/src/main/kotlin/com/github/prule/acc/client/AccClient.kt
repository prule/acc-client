package com.github.prule.acc.client

import com.github.prule.acc.messages.AccBroadcastingClient
import com.github.prule.acc.messages.AccBroadcastingInbound
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * Example wiring.
 *
 * Listener ordering contract for [AccClient.connect]:
 * 1. [LoggingListener] (or any pure observer) — no state
 * 2. [ContextUpdater] — populates [ClientContext]; MUST run before [SessionDetector]
 * 3. [SessionDetector] — snapshots [ClientContext] on session start
 *
 * [ClientContext] survives reconnects within a single [AccClient] lifetime, so cached track + cars
 * stay available across socket drops.
 */
suspend fun main() {
  val context = ClientContext()
  AccClient(
      AccClientConfiguration(
        "Test",
        port = 9000,
        //        serverIp = "127.0.0.1",
        serverIp = "desktop-chff66k",
        //            serverIp = "192.168.86.116",
      )
    )
    .connect(
      listOf(
        LoggingListener(),
        ContextUpdater(context),
        SessionDetector(
          context,
          listOf(RecordingSessionListener(java.nio.file.Path.of("./recordings"))),
        ),
      )
    )
}

/**
 * Manages the UDP socket lifecycle to the ACC broadcasting server: register → receive → reconnect
 * on socket timeout.
 *
 * Listeners passed to [connect] receive every inbound message. Ordering matters when listeners have
 * a producer/consumer relationship — see the contract on [main]. In particular, [ContextUpdater]
 * must precede [SessionDetector].
 */
class AccClient(private val configuration: AccClientConfiguration) {
  private val logger = LoggerFactory.getLogger(javaClass)
  private val client = AccBroadcastingClient()
  private var running = false

  /** Attempts to connect to the server over UDP using the given connection */
  suspend fun connect(listeners: List<MessageListener<AccBroadcastingInbound>>) {
    logger.info("Connecting to server with configuration {}", configuration)
    running = true

    val registerCommand =
      client.buildRegisterCommandApplication(
        configuration.name,
        configuration.connectionPassword,
        configuration.updateMillis,
        configuration.connectionPassword,
      )

    withContext(Dispatchers.IO) {
      while (running) {
        logger.debug("Opening socket and registering")
        DatagramSocket().use { socket ->
          socket.soTimeout = configuration.connectTimeout.inWholeMilliseconds.toInt()

          val job = launch {
            MessageReceiver(socket, listeners) { buffer -> AccBroadcastingInbound(buffer) }.start()
          }

          delay(1000.milliseconds)
          send(socket, registerCommand)
          logger.debug("Sent register command, listening for data")

          job.join()
        }

        if (running) {
          logger.debug("Session ended, waiting before reconnecting")
          delay(1000.milliseconds)
        }
      }
    }
  }

  fun stop() {
    running = false
  }

  fun send(socket: DatagramSocket, bytes: ByteArray) {
    val handshakePacket =
      DatagramPacket(
        bytes,
        bytes.size,
        InetAddress.getByName(configuration.serverIp),
        configuration.port,
      )

    socket.send(handshakePacket)
  }
}

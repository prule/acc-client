package io.github.prule.acc.client

import io.github.prule.acc.messages.AccBroadcastingClient
import io.github.prule.acc.messages.AccBroadcastingInbound
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

suspend fun main() {
    AccClient(
        AccClientConfiguration(
            "Test",
            port = 9996,
            serverIp = "127.0.0.1",
//            serverIp = "192.168.86.116",
        ),
    ).connect(
        listOf(
            LoggingListener(),
            CsvWriterListener(
                java.nio.file.Path
                    .of("./recordings"),
            ),
            RegistrationResultListener(),
        ),
    )
}

class AccClient(
    private val configuration: AccClientConfiguration,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val client = AccBroadcastingClient()
    private var connectionId: Int = 0
    private var running = false

    /**
     * Attempts to connect to the server over UDP using the given connection
     */
    suspend fun connect(listeners: List<MessageListener<AccBroadcastingInbound>>) {
        logger.debug("Connecting to server")
        running = true

        val registerCommand =
            client.buildRegisterCommandApplication(
                configuration.name,
                configuration.connectionPassword,
                configuration.updateMillis,
                configuration.connectionPassword,
            )

        withContext(Dispatchers.IO) {
            DatagramSocket().use { socket ->
                socket.soTimeout = 2000

                launch {
                    MessageReceiver(
                        socket,
                        listeners,
                    ) { buffer -> AccBroadcastingInbound(buffer) }.start()
                }

                delay(1000)
                send(socket, registerCommand)
                logger.debug("Sent register command, listening for data")

                while (running) {
                    delay(1000)
                }
            }
        }
    }

    fun stop() {
        running = false
    }

    fun send(
        socket: DatagramSocket,
        bytes: ByteArray,
    ) {
        val handshakePacket =
            DatagramPacket(bytes, bytes.size, InetAddress.getByName(configuration.serverIp), configuration.port)

        socket.send(handshakePacket)
    }
}

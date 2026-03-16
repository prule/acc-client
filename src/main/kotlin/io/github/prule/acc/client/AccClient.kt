package io.github.prule.acc.client

import io.github.prule.acc.messages.AccBroadcastingClient
import io.github.prule.acc.messages.AccBroadcastingInbound
import org.slf4j.LoggerFactory
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

fun main() {
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

    /**
     * Attempts to connect to the server over UDP using the given connection
     */
    fun connect(listeners: List<MessageListener<AccBroadcastingInbound>>) {
        logger.debug("Connecting to server")

        val registerCommand =
            client.buildRegisterCommandApplication(
                configuration.name,
                configuration.connectionPassword,
                configuration.updateMillis,
                configuration.connectionPassword,
            )

        DatagramSocket().use { socket ->
            socket.soTimeout = 2000

            send(socket, registerCommand)
            logger.debug("Sent register command, listening for data")

            MessageReceiver(
                socket,
                listeners,
            ) { buffer -> AccBroadcastingInbound(buffer) }.start()
        }
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

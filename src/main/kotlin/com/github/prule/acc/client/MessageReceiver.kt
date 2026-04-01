package com.github.prule.acc.client

import io.kaitai.struct.ByteBufferKaitaiStream
import kotlinx.coroutines.DelicateCoroutinesApi
import org.slf4j.LoggerFactory
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MessageReceiver<T>(
    private val socket: DatagramSocket,
    private val listeners: List<MessageListener<T>>,
    private val messageFactory: (bytes: ByteBufferKaitaiStream) -> T,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @OptIn(DelicateCoroutinesApi::class)
    fun start() {
        listeners.forEach { listener -> listener.onStart() }
        try {
            while (true) {
                try {
                    val receiveBuffer = ByteArray(2048)
                    val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
                    socket.receive(receivePacket)
                    val bytes = receiveBuffer.copyOfRange(0, receivePacket.length)
                    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                    val message = messageFactory(ByteBufferKaitaiStream(buffer))
                    val sender = MessageSender(socket, receivePacket.socketAddress)
                    listeners.forEach { listener -> listener.onMessage(bytes, message, sender) }
                } catch (e: java.net.SocketTimeoutException) {
                    logger.debug("Socket timed out. Session ended.")
                    continue
                } catch (e: Exception) {
                    logger.error("An error occurred: $e", e)
                    break
                }
            }
        } finally {
            listeners.forEach { listener -> listener.onStop() }
        }
    }
}

class MessageSender(
    val socket: DatagramSocket,
    val socketAddress: SocketAddress,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun send(bytes: ByteArray) {
        logger.debug("Sending bytes: ${bytes.toHexString()}")
        val responsePacket = DatagramPacket(bytes, bytes.size, socketAddress)
        socket.send(responsePacket)
    }
}

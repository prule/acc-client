package io.github.prule.acc.client

import io.github.prule.acc.messages.AccBroadcastingClient
import io.github.prule.acc.messages.AccBroadcastingInbound
import org.slf4j.LoggerFactory

class RegistrationResultListener : MessageListener<AccBroadcastingInbound> {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val client = AccBroadcastingClient()

    override fun onMessage(
        bytes: ByteArray,
        message: AccBroadcastingInbound,
        messageSender: MessageSender,
    ) {
        if (AccBroadcastingInbound.InboundMsgType.REGISTRATION_RESULT == message.msgType()) {
            val result = message.body() as AccBroadcastingInbound.RegistrationResult
            val connectionId = result.connectionId()
            logger.debug("Sending entry list request")
            messageSender.send(client.buildRequestEntryList(connectionId))
            logger.debug("Sending track data request")
            messageSender.send(client.buildRequestTrackData(connectionId))
        }
    }
}

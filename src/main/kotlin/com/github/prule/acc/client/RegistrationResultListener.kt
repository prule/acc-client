package com.github.prule.acc.client

import com.github.prule.acc.messages.AccBroadcastingClient
import com.github.prule.acc.messages.AccBroadcastingInbound
import org.slf4j.LoggerFactory

class RegistrationResultListener(private var clientState: ClientState) : MessageListener<AccBroadcastingInbound> {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val client = AccBroadcastingClient()
    private val carModelRepository = CarModelRepository()

    override fun onMessage(
        bytes: ByteArray,
        message: AccBroadcastingInbound,
        messageSender: MessageSender,
    ) {
        if (AccBroadcastingInbound.InboundMsgType.REGISTRATION_RESULT == message.msgType()) {
            val result = message.body() as AccBroadcastingInbound.RegistrationResult
            val connectionId = result.connectionId()
            clientState.connectionId = connectionId
            logger.debug("Received registration result - connectionId = $connectionId")
            logger.debug("Sending entry list request")
            messageSender.send(client.buildRequestEntryList(connectionId))
            logger.debug("Sending track data request")
            messageSender.send(client.buildRequestTrackData(connectionId))
        }
        if (AccBroadcastingInbound.InboundMsgType.REALTIME_UPDATE == message.msgType()) {
            val result = message.body() as AccBroadcastingInbound.RealtimeUpdate
            clientState.focusedCarIndex = result.focusedCarIndex()
            logger.debug("Received realtime update request - focusedCarIndex = ${clientState.focusedCarIndex}")
        }
        if (AccBroadcastingInbound.InboundMsgType.TRACK_DATA == message.msgType()) {
            val result = message.body() as AccBroadcastingInbound.TrackData
            clientState.track = result.trackName().data()
        }
        if (AccBroadcastingInbound.InboundMsgType.ENTRY_LIST == message.msgType()) {
            val result = message.body() as AccBroadcastingInbound.EntryList
            clientState.carList = result.carIndexes().toList()
        }
        if (AccBroadcastingInbound.InboundMsgType.ENTRY_LIST_CAR == message.msgType()) {
            val result = message.body() as AccBroadcastingInbound.EntryListCar
            if (result.carId() == clientState.focusedCarIndex) {
                clientState.car = carModelRepository.findById(result.carId())
            }
        }
        logger.info("Client state: {}", clientState)
    }
}

package com.github.prule.acc.client

import com.github.prule.acc.messages.AccBroadcastingInbound
import org.slf4j.LoggerFactory

class SessionListener(private var sessionState: SessionState) :
  MessageListener<AccBroadcastingInbound> {
  private val logger = LoggerFactory.getLogger(javaClass)
  private val carModelRepository = CarModelRepository()

  override fun onMessage(
    bytes: ByteArray,
    message: AccBroadcastingInbound,
    messageSender: MessageSender,
  ) {
    if (AccBroadcastingInbound.InboundMsgType.TRACK_DATA == message.msgType()) {
      val result = message.body() as AccBroadcastingInbound.TrackData
      sessionState.track = result.trackName().data()
    }
    if (AccBroadcastingInbound.InboundMsgType.ENTRY_LIST == message.msgType()) {
      val result = message.body() as AccBroadcastingInbound.EntryList
      sessionState.carList = result.carIndexes().toList()
    }
    if (AccBroadcastingInbound.InboundMsgType.ENTRY_LIST_CAR == message.msgType()) {
      val result = message.body() as AccBroadcastingInbound.EntryListCar
      sessionState.carMap[result.carId()] = result
    }
    if (AccBroadcastingInbound.InboundMsgType.REALTIME_UPDATE == message.msgType()) {
      val result = message.body() as AccBroadcastingInbound.RealtimeUpdate
      sessionState.focusedCarIndex = result.focusedCarIndex()
    }
    if (AccBroadcastingInbound.InboundMsgType.REALTIME_CAR_UPDATE == message.msgType()) {
      val result = message.body() as AccBroadcastingInbound.RealtimeCarUpdate
      sessionState.laps.getOrPut(result.carIndex()) { mutableMapOf() }[result.laps()] = result
    }
    if (AccBroadcastingInbound.InboundMsgType.BROADCASTING_EVENT == message.msgType()) {
      val result = message.body() as AccBroadcastingInbound.BroadcastingEvent
      sessionState.lapsCompleted.getOrPut(result.carId()) { mutableListOf() }.add(result)
    }
    logger.info("Session state: {}", sessionState)
  }
}

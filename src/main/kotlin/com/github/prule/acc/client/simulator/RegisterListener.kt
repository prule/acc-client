package com.github.prule.acc.client.simulator

import com.github.prule.acc.client.MessageListener
import com.github.prule.acc.client.MessageSender
import com.github.prule.acc.messages.AccBroadcastingOutbound
import java.net.DatagramSocket

class RegisterListener(val socket: DatagramSocket, val eventPlayer: EventPlayer) :
  MessageListener<AccBroadcastingOutbound> {
  override fun onMessage(
    bytes: ByteArray,
    message: AccBroadcastingOutbound,
    messageSender: MessageSender,
  ) {
    if (AccBroadcastingOutbound.OutboundMsgType.REGISTER_COMMAND_APPLICATION == message.msgType()) {
      sendRegistrationResult(messageSender)
      eventPlayer.sendPackets(messageSender)
    }
  }

  fun sendRegistrationResult(messageSender: MessageSender) {
    val bytes = "010500000001010000".hexToByteArray()
    messageSender.send(bytes)
  }
}

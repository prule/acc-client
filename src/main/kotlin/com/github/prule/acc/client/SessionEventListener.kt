package com.github.prule.acc.client

import com.github.prule.acc.messages.AccBroadcastingInbound

interface SessionEventListener {
  fun onSessionStart(preamble: SessionPreamble) {}

  fun onSessionMessage(bytes: ByteArray, message: AccBroadcastingInbound, sender: MessageSender) {}

  fun onSessionStop() {}
}
